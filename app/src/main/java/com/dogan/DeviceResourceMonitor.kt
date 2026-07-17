package com.dogan

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import java.io.RandomAccessFile

/** Samples CPU and RAM usage for telemetry. */
class DeviceResourceMonitor(private val context: Context) {
    private var lastCpuTotal: Long = 0L
    private var lastCpuIdle: Long = 0L

    fun cpuUsagePercent(): Double {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (parts.size < 4) return 0.0
            val idle = parts[3]
            val total = parts.sum()
            val totalDelta = total - lastCpuTotal
            val idleDelta = idle - lastCpuIdle
            lastCpuTotal = total
            lastCpuIdle = idle
            if (totalDelta <= 0) return 0.0
            ((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0
        } catch (_: Exception) {
            0.0
        }
    }

    fun ramUsagePercent(): Double {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val total = info.totalMem.toDouble()
            val avail = info.availMem.toDouble()
            if (total <= 0) return 0.0
            ((total - avail) / total) * 100.0
        } catch (_: Exception) {
            0.0
        }
    }

    fun processId(): Int = Process.myPid()
}
