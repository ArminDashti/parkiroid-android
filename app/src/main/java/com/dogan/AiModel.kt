package com.dogan

/** Selectable on-device detection model identifiers (embedded YOLO26 NCNN weights). */
enum class AiModel(val displayName: String) {
    YOLO26_NANO("YOLO26 Nano"),
    YOLO26_SMALL("YOLO26 Small"),
    YOLO26_MEDIUM("YOLO26 Medium");

    fun toStoredValue(): String = when (this) {
        YOLO26_NANO -> "yolo26_nano"
        YOLO26_SMALL -> "yolo26_small"
        YOLO26_MEDIUM -> "yolo26_medium"
    }

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): AiModel {
            if (value.isNullOrBlank()) return YOLO26_NANO
            val normalized = value.trim().lowercase()
            entries.find { it.toStoredValue() == normalized || it.name.equals(normalized, ignoreCase = true) }
                ?.let { return it }
            // Legacy YOLOv8 / MobileNet IDs from older installs or server settings.
            return when (normalized) {
                "yolov8_nano", "yolov8_small", "mobilenet_ssd" -> YOLO26_NANO
                else -> YOLO26_NANO
            }
        }
    }
}
