package com.dogan

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/** ICMP ping helper for connectivity diagnostics. */
object PingHelper {
    private val timePattern = Pattern.compile("time[=<]([\\d.]+)\\s*ms", Pattern.CASE_INSENSITIVE)

    fun pingAverageMs(host: String, packetCount: Int): Long {
        val samples = mutableListOf<Long>()
        repeat(packetCount) {
            pingOnce(host)?.let { samples.add(it) }
        }
        if (samples.isEmpty()) return -1L
        return samples.average().toLong()
    }

    fun pingInternetAverageMs(): Long {
        val samples = mutableListOf<Long>()
        listOf("1.1.1.1", "8.8.8.8").forEach { host ->
            repeat(4) {
                pingOnce(host)?.let { samples.add(it) }
            }
        }
        if (samples.isEmpty()) return -1L
        return samples.average().toLong()
    }

    private fun pingOnce(host: String): Long? {
        return try {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "3", host)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val exit = process.waitFor()
            if (exit != 0) return null
            val matcher = timePattern.matcher(output)
            if (matcher.find()) {
                matcher.group(1)?.toDoubleOrNull()?.toLong()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
