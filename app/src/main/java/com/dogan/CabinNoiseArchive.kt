package com.dogan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ordered cabin noise archival for diagnostic purposes. */
class CabinNoiseArchive(context: Context) {
    private val audioDir = File(context.filesDir, "diagnostics/audio").also { it.mkdirs() }
    private val indexDb = CabinNoiseIndexDb(context)

    @Volatile
    private var segmentId = 0L
    @Volatile
    private var currentSegmentFile: File? = null
    @Volatile
    private var currentRaf: RandomAccessFile? = null
    @Volatile
    private var segmentStartMs = 0L
    @Volatile
    private var rmsPeak = 0.0

    companion object {
        const val SAMPLE_RATE = 16_000
        const val SEGMENT_DURATION_MS = 10_000L
        const val MAX_SEGMENTS = 500
    }

    fun startNewSegment() {
        closeCurrentSegment(null, null)
        segmentId++
        segmentStartMs = System.currentTimeMillis()
        rmsPeak = 0.0
        val file = File(audioDir, "segment_${segmentId}.wav")
        currentSegmentFile = file
        currentRaf = RandomAccessFile(file, "rw")
        writeWavHeader(currentRaf!!, 0)
        indexDb.insertSegment(segmentId, segmentStartMs, null, null)
    }

    fun writePcmSamples(samples: ShortArray, length: Int, rms: Double) {
        val raf = currentRaf ?: return
        if (rms > rmsPeak) rmsPeak = rms

        val elapsed = System.currentTimeMillis() - segmentStartMs
        if (elapsed >= SEGMENT_DURATION_MS) {
            closeCurrentSegment(null, null)
            startNewSegment()
            return
        }

        val buffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until length) {
            buffer.putShort(samples[i])
        }
        raf.write(buffer.array())
    }

    fun closeCurrentSegment(linkedAlertId: String?, mode: String?) {
        val raf = currentRaf ?: return
        val file = currentSegmentFile ?: return
        val dataSize = (raf.length() - 44).toInt().coerceAtLeast(0)
        raf.seek(0)
        writeWavHeader(raf, dataSize)
        raf.close()
        currentRaf = null
        indexDb.updateSegment(segmentId, System.currentTimeMillis(), rmsPeak, linkedAlertId, mode)
        enforceRetention()
    }

    fun stop() {
        closeCurrentSegment(null, null)
    }

    fun getPendingUploads(): List<CabinNoiseSegment> = indexDb.getPendingUploads()

    fun markUploaded(id: Long) = indexDb.markUploaded(id)

    private fun enforceRetention() {
        indexDb.trimToMax(MAX_SEGMENTS)
        val files = audioDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_SEGMENTS) {
            files.take(files.size - MAX_SEGMENTS).forEach { it.delete() }
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)
        raf.write(header.array())
    }

    data class CabinNoiseSegment(
        val id: Long,
        val file: File,
        val startMs: Long,
        val endMs: Long,
        val rmsPeak: Double,
        val linkedAlertId: String?,
        val mode: String?,
    )

    private class CabinNoiseIndexDb(context: Context) :
        SQLiteOpenHelper(context, "dogan_cabin_noise.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE cabin_segments (
                    id INTEGER PRIMARY KEY,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER,
                    rms_peak REAL,
                    linked_alert_id TEXT,
                    mode TEXT,
                    upload_status TEXT DEFAULT 'pending',
                    file_path TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

        fun insertSegment(id: Long, startMs: Long, alertId: String?, mode: String?) {
            writableDatabase.execSQL(
                "INSERT INTO cabin_segments (id, start_ms, linked_alert_id, mode, file_path) VALUES (?, ?, ?, ?, ?)",
                arrayOf(id, startMs, alertId, mode, "segment_$id.wav"),
            )
        }

        fun updateSegment(id: Long, endMs: Long, rmsPeak: Double, alertId: String?, mode: String?) {
            writableDatabase.execSQL(
                "UPDATE cabin_segments SET end_ms=?, rms_peak=?, linked_alert_id=COALESCE(?, linked_alert_id), mode=COALESCE(?, mode) WHERE id=?",
                arrayOf(endMs, rmsPeak, alertId, mode, id),
            )
        }

        fun getPendingUploads(): List<CabinNoiseSegment> {
            val cursor = readableDatabase.rawQuery(
                "SELECT id, start_ms, end_ms, rms_peak, linked_alert_id, mode, file_path FROM cabin_segments WHERE upload_status='pending' AND end_ms IS NOT NULL ORDER BY id ASC",
                null,
            )
            val segments = mutableListOf<CabinNoiseSegment>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                segments.add(
                    CabinNoiseSegment(
                        id = id,
                        file = File(contextOverride.filesDir, "diagnostics/audio/${cursor.getString(6)}"),
                        startMs = cursor.getLong(1),
                        endMs = cursor.getLong(2),
                        rmsPeak = cursor.getDouble(3),
                        linkedAlertId = cursor.getString(4),
                        mode = cursor.getString(5),
                    ),
                )
            }
            cursor.close()
            return segments
        }

        fun markUploaded(id: Long) {
            writableDatabase.execSQL("UPDATE cabin_segments SET upload_status='uploaded' WHERE id=?", arrayOf(id))
        }

        fun trimToMax(max: Int) {
            writableDatabase.execSQL(
                "DELETE FROM cabin_segments WHERE id NOT IN (SELECT id FROM cabin_segments ORDER BY id DESC LIMIT ?)",
                arrayOf(max),
            )
        }

        companion object {
            lateinit var contextOverride: Context
        }

        init {
            contextOverride = context
        }
    }
}
