package com.parkiroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

data class VehicleDetection(val label: String, val confidence: Float, val bounds: RectF)

data class VehicleDetectionResult(val detections: List<VehicleDetection>)

class ParkiroidObjectDetector(context: Context) : AutoCloseable {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val inputHeight: Int
    private val inputWidth: Int
    private val letterboxBitmap: Bitmap
    private val pixelScratch: IntArray
    private val inputBuffer: FloatArray

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply {
            try {
                addNnapi()
            } catch (e: Exception) {
                Log.w(TAG, "NNAPI delegate unavailable, using CPU", e)
            }
            setIntraOpNumThreads(4)
        }
        session = environment.createSession(modelBytes, options)
        inputName = session.inputNames.first()
        val inputShape = (session.inputInfo[inputName]?.info as? TensorInfo)?.shape
        inputHeight = inputShape?.getOrNull(2)?.toInt() ?: INPUT_SIZE
        inputWidth = inputShape?.getOrNull(3)?.toInt() ?: INPUT_SIZE
        letterboxBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        pixelScratch = IntArray(inputWidth * inputHeight)
        inputBuffer = FloatArray(3 * inputWidth * inputHeight)
        Log.i(TAG, "Loaded $MODEL_ASSET (${inputWidth}x$inputHeight)")
    }

    fun detect(bitmap: Bitmap, confidenceThreshold: Float = SettingsStore.DEFAULT_CONFIDENCE_THRESHOLD): VehicleDetectionResult {
        val letterbox = letterbox(bitmap)
        bitmapToNchwFloat(letterbox.bitmap, inputBuffer)
        val detections = runInference(
            input = inputBuffer,
            confidenceThreshold = confidenceThreshold,
            letterbox = letterbox,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height
        )
        return VehicleDetectionResult(detections)
    }

    override fun close() {
        session.close()
        letterboxBitmap.recycle()
    }

    companion object {
        private const val TAG = "ParkiroidObjectDetector"
        const val MODEL_ASSET = "yolo26n.onnx"
        const val INPUT_SIZE = 640
        const val MAX_RESULTS = 10
        val VEHICLE_CLASS_IDS = setOf(2, 3, 5, 7) // car, motorcycle, bus, truck

        private val COCO_LABELS = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        )

        fun decodeJpegForDetection(jpegFile: File): Bitmap? {
            val bitmap = BitmapFactory.decodeFile(jpegFile.absolutePath) ?: return null
            return applyExifOrientation(jpegFile, bitmap)
        }

        fun summarize(result: VehicleDetectionResult): String {
            if (result.detections.isEmpty()) return "no vehicles"
            return result.detections.joinToString(", ") { detection ->
                "${detection.label} (${"%.0f".format(detection.confidence * 100)}%)"
            }
        }

        fun logResult(result: VehicleDetectionResult) {
            Log.d(TAG, "Object detection: ${summarize(result)}")
        }

        private fun labelForClassId(classId: Int): String =
            COCO_LABELS.getOrNull(classId) ?: "class$classId"

        private fun applyExifOrientation(jpegFile: File, bitmap: Bitmap): Bitmap {
            val orientation = ExifInterface(jpegFile.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            return rotated
        }
    }

    private data class LetterboxResult(
        val bitmap: Bitmap,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private fun letterbox(source: Bitmap): LetterboxResult {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val scale = min(inputWidth / sourceWidth.toFloat(), inputHeight / sourceHeight.toFloat())
        val resizedWidth = (sourceWidth * scale).roundToInt()
        val resizedHeight = (sourceHeight * scale).roundToInt()
        val padX = ((inputWidth - resizedWidth) / 2f - 0.1f).roundToInt().toFloat()
        val padY = ((inputHeight - resizedHeight) / 2f - 0.1f).roundToInt().toFloat()

        val canvas = Canvas(letterboxBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val scaled = Bitmap.createScaledBitmap(source, resizedWidth, resizedHeight, true)
        canvas.drawBitmap(scaled, padX, padY, null)
        if (scaled !== source) scaled.recycle()
        return LetterboxResult(letterboxBitmap, scale, padX, padY)
    }

    private fun bitmapToNchwFloat(bitmap: Bitmap, out: FloatArray) {
        val width = bitmap.width
        val height = bitmap.height
        bitmap.getPixels(pixelScratch, 0, width, 0, 0, width, height)
        val planeSize = width * height
        for (i in pixelScratch.indices) {
            val pixel = pixelScratch[i]
            out[i] = ((pixel shr 16) and 0xFF) / 255f
            out[planeSize + i] = ((pixel shr 8) and 0xFF) / 255f
            out[2 * planeSize + i] = (pixel and 0xFF) / 255f
        }
    }

    private fun runInference(
        input: FloatArray,
        confidenceThreshold: Float,
        letterbox: LetterboxResult,
        sourceWidth: Int,
        sourceHeight: Int
    ): List<VehicleDetection> {
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        ).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { outputs ->
                val rawOutput = (outputs[0].value as Array<Array<FloatArray>>)[0]
                val detections = mutableListOf<VehicleDetection>()
                for (row in rawOutput) {
                    if (row.size < 6) continue
                    val confidence = row[4]
                    if (confidence < confidenceThreshold) continue
                    val classId = row[5].toInt()
                    if (classId !in VEHICLE_CLASS_IDS) continue
                    detections.add(
                        VehicleDetection(
                            label = labelForClassId(classId),
                            confidence = confidence,
                            bounds = mapBoxToSource(row, letterbox, sourceWidth, sourceHeight)
                        )
                    )
                }
                return detections.sortedByDescending { it.confidence }.take(MAX_RESULTS)
            }
        }
    }

    private fun mapBoxToSource(
        row: FloatArray,
        letterbox: LetterboxResult,
        sourceWidth: Int,
        sourceHeight: Int
    ): RectF {
        val x1 = (row[0] - letterbox.padX) / letterbox.scale
        val y1 = (row[1] - letterbox.padY) / letterbox.scale
        val x2 = (row[2] - letterbox.padX) / letterbox.scale
        val y2 = (row[3] - letterbox.padY) / letterbox.scale
        return RectF(
            x1.coerceIn(0f, sourceWidth.toFloat()),
            y1.coerceIn(0f, sourceHeight.toFloat()),
            x2.coerceIn(0f, sourceWidth.toFloat()),
            y2.coerceIn(0f, sourceHeight.toFloat())
        )
    }
}
