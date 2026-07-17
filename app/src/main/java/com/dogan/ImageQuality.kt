package com.dogan

/** JPEG quality and optional resize target for capture or detection pipelines. */
enum class ImageQuality(
    val label: String,
    val jpegQuality: Int,
    val maxDimension: Int?,
) {
    VERY_LOW("Very Low", 25, 320),
    LOW("Low", 45, 480),
    BALANCED("Balanced", 65, 640),
    HIGH("High", 80, 960),
    ORIGINAL("Original", 95, null);

    fun toStoredValue(): String = name.lowercase()

    companion object {
        /** Qualities exposed in settings UI. */
        val uiOptions = listOf(LOW, BALANCED, HIGH, ORIGINAL)

        fun fromStoredValue(value: String?): ImageQuality =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BALANCED
    }
}
