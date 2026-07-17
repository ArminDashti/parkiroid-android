package com.dogan

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Local ring-buffer of detection frames with bounding-box metadata. */
class FrameHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dogan_frame_history.db", null, 1) {

    private val filesDir = File(context.applicationContext.filesDir, "frame_history").also { it.mkdirs() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE frame_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                mode TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                image_path TEXT NOT NULL,
                image_width INTEGER NOT NULL,
                image_height INTEGER NOT NULL,
                detections_json TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_frame_history_mode ON frame_history(mode, created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun append(
        mode: OperatingMode,
        bitmap: Bitmap,
        detections: List<VehicleDetection>,
        maxFrames: Int,
    ) {
        if (mode == OperatingMode.OFF) return
        val modeKey = mode.toStoredValue()
        val file = File(filesDir, "${modeKey}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        val detectionsJson = JSONArray()
        for (detection in detections) {
            detectionsJson.put(
                JSONObject()
                    .put("label", detection.label)
                    .put("confidence", detection.confidence.toDouble())
                    .put("left", detection.bounds.left.toDouble())
                    .put("top", detection.bounds.top.toDouble())
                    .put("right", detection.bounds.right.toDouble())
                    .put("bottom", detection.bounds.bottom.toDouble()),
            )
        }
        val db = writableDatabase
        val values = ContentValues().apply {
            put("mode", modeKey)
            put("created_at", System.currentTimeMillis())
            put("image_path", file.absolutePath)
            put("image_width", bitmap.width)
            put("image_height", bitmap.height)
            put("detections_json", detectionsJson.toString())
        }
        db.insert("frame_history", null, values)
        trim(modeKey, maxFrames.coerceAtLeast(1))
    }

    fun list(mode: OperatingMode, limit: Int = 200): List<HistoryFrame> {
        val modeKey = mode.toStoredValue()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT id, mode, created_at, image_path, image_width, image_height, detections_json
            FROM frame_history
            WHERE mode = ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(modeKey, limit.toString()),
        )
        val rows = mutableListOf<HistoryFrame>()
        while (cursor.moveToNext()) {
            rows.add(
                HistoryFrame(
                    id = cursor.getLong(0),
                    mode = OperatingMode.fromStoredValue(cursor.getString(1)),
                    createdAt = cursor.getLong(2),
                    imagePath = cursor.getString(3),
                    imageWidth = cursor.getInt(4),
                    imageHeight = cursor.getInt(5),
                    detections = parseDetections(cursor.getString(6)),
                ),
            )
        }
        cursor.close()
        return rows
    }

    fun flush(mode: OperatingMode? = null) {
        val db = writableDatabase
        if (mode == null) {
            val cursor = db.rawQuery("SELECT image_path FROM frame_history", null)
            while (cursor.moveToNext()) {
                File(cursor.getString(0)).delete()
            }
            cursor.close()
            db.execSQL("DELETE FROM frame_history")
            return
        }
        val modeKey = mode.toStoredValue()
        val cursor = db.rawQuery(
            "SELECT image_path FROM frame_history WHERE mode = ?",
            arrayOf(modeKey),
        )
        while (cursor.moveToNext()) {
            File(cursor.getString(0)).delete()
        }
        cursor.close()
        db.execSQL("DELETE FROM frame_history WHERE mode = ?", arrayOf(modeKey))
    }

    private fun trim(modeKey: String, maxFrames: Int) {
        val db = writableDatabase
        val cursor = db.rawQuery(
            """
            SELECT id, image_path FROM frame_history
            WHERE mode = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            arrayOf(modeKey),
        )
        var index = 0
        while (cursor.moveToNext()) {
            index++
            if (index <= maxFrames) continue
            File(cursor.getString(1)).delete()
            db.delete("frame_history", "id = ?", arrayOf(cursor.getLong(0).toString()))
        }
        cursor.close()
    }

    private fun parseDetections(json: String): List<VehicleDetection> {
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        VehicleDetection(
                            label = obj.getString("label"),
                            confidence = obj.getDouble("confidence").toFloat(),
                            bounds = RectF(
                                obj.getDouble("left").toFloat(),
                                obj.getDouble("top").toFloat(),
                                obj.getDouble("right").toFloat(),
                                obj.getDouble("bottom").toFloat(),
                            ),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class HistoryFrame(
        val id: Long,
        val mode: OperatingMode,
        val createdAt: Long,
        val imagePath: String,
        val imageWidth: Int,
        val imageHeight: Int,
        val detections: List<VehicleDetection>,
    )
}
