package com.dogan

/** Uploads pending telemetry rows and flushes SQLite on success. */
class TelemetryUploader(
    private val apiClient: DoganApiClient,
    private val database: TelemetryDatabase,
) {
    fun uploadPending(baseUrl: String): UploadStats {
        val rows = database.peekPending()
        if (rows.isEmpty()) return UploadStats(0, 0, 0)

        var uploaded = 0
        var failed = 0
        val flushedIds = mutableListOf<Long>()

        for (row in rows) {
            val ok = apiClient.submitTelemetry(baseUrl, row.payload)
            if (ok) {
                uploaded++
                flushedIds.add(row.id)
            } else {
                failed++
            }
        }

        if (flushedIds.isNotEmpty()) {
            database.flushUploaded(flushedIds)
        }

        return UploadStats(uploaded, failed, database.pendingCount())
    }

    data class UploadStats(val uploaded: Int, val failed: Int, val remaining: Int)
}
