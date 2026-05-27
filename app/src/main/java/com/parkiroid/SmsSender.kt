package com.parkiroid

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SmsSender {
    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun send(context: Context, phoneNumber: String, message: String): Result<Unit> {
        if (!canSend(context)) {
            return Result.failure(SecurityException("SEND_SMS permission not granted"))
        }
        val to = phoneNumber.trim()
        val body = message.trim()
        if (to.isEmpty() || body.isEmpty()) {
            return Result.failure(IllegalArgumentException("Phone number and message must not be empty"))
        }
        return try {
            val manager = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            val parts = manager.divideMessage(body)
            if (parts.size <= 1) {
                manager.sendTextMessage(to, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendToAll(context: Context, phoneNumbers: Iterable<String>, message: String): Int {
        var sent = 0
        for (number in phoneNumbers) {
            if (send(context, number, message).isSuccess) sent++
        }
        return sent
    }
}
