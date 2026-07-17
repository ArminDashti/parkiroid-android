package com.dogan

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** SQLite buffer for telemetry payloads; flushed after successful server upload. */
class TelemetryDatabase(context: Context) : SQLiteOpenHelper(context, "dogan_telemetry.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE telemetry_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                upload_status TEXT NOT NULL DEFAULT 'pending'
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun enqueue(payload: JSONObject): Long {
        val db = writableDatabase
        db.execSQL(
            "INSERT INTO telemetry_queue (payload, created_at, upload_status) VALUES (?, ?, 'pending')",
            arrayOf(payload.toString(), System.currentTimeMillis()),
        )
        val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
        cursor.moveToFirst()
        val id = cursor.getLong(0)
        cursor.close()
        return id
    }

    fun peekPending(limit: Int = 10): List<TelemetryRow> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, payload, created_at FROM telemetry_queue WHERE upload_status = 'pending' ORDER BY id ASC LIMIT ?",
            arrayOf(limit.toString()),
        )
        val rows = mutableListOf<TelemetryRow>()
        while (cursor.moveToNext()) {
            rows.add(
                TelemetryRow(
                    id = cursor.getLong(0),
                    payload = JSONObject(cursor.getString(1)),
                    createdAt = cursor.getLong(2),
                ),
            )
        }
        cursor.close()
        return rows
    }

    /** Deletes uploaded rows from SQLite (flush after successful server upload). */
    fun flushUploaded(ids: List<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        val placeholders = ids.joinToString(",") { "?" }
        db.execSQL("DELETE FROM telemetry_queue WHERE id IN ($placeholders)", ids.toTypedArray())
        AppLogger.info("Telemetry", "Flushed ${ids.size} row(s) from SQLite")
    }

    fun pendingCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM telemetry_queue WHERE upload_status = 'pending'", null)
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()
        return count
    }

    fun purgeOlderThan(retentionHours: Int) {
        val cutoff = System.currentTimeMillis() - retentionHours * 3_600_000L
        val db = writableDatabase
        db.execSQL("DELETE FROM telemetry_queue WHERE created_at < ?", arrayOf(cutoff))
    }

    data class TelemetryRow(val id: Long, val payload: JSONObject, val createdAt: Long)
}
