package com.dogan

/** WebRTC streaming mode. */
enum class StreamMode(val displayName: String) {
    VIDEO_ONLY("Video only"),
    AUDIO_ONLY("Audio only"),
    VIDEO_AUDIO("Video + Audio");

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): StreamMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase() }
                ?: VIDEO_AUDIO
    }
}
