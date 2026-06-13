package com.parkiroid

import android.content.Context
import android.provider.Settings

object DeviceIdentity {
    fun resolveDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            "parkiroid-${context.packageName}"
        }
    }
}
