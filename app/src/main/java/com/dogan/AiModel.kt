package com.dogan

/** Selectable on-device detection model identifiers. */
enum class AiModel(val displayName: String) {
    YOLOV8_NANO("YOLOv8 Nano"),
    YOLOV8_SMALL("YOLOv8 Small"),
    MOBILENET_SSD("MobileNet SSD");

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): AiModel =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: YOLOV8_NANO
    }
}
