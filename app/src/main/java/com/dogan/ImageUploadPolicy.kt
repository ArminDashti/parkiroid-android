package com.dogan

/** When capture/processed images are sent to the server. */
enum class ImageUploadPolicy(val displayName: String) {
    AUTO("Auto (send with telemetry)"),
    ON_DEMAND("On demand only");

    fun toStoredValue(): String = when (this) {
        AUTO -> "auto"
        ON_DEMAND -> "on_demand"
    }

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): ImageUploadPolicy = when (value?.lowercase()) {
            "auto" -> AUTO
            "on_demand" -> ON_DEMAND
            else -> ON_DEMAND
        }
    }
}
