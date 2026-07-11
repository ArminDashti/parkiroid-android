package com.dogan

import android.content.Context
import android.provider.Settings

/** Resolves a stable device identifier for Dogan API requests. */
object DeviceIdentity {
    /** Returns the Android ID, or a package-scoped fallback when the ID is missing or invalid. */
    fun resolveDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            "dogan-${context.packageName}"
        }
    }
}
