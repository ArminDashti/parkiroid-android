package com.dogan

/** Whether recorded detection video includes microphone audio. */
enum class VideoAudioMode(val displayName: String) {
    VIDEO_ONLY("Video only"),
    VIDEO_AND_SOUND("Video and sound");

    fun toStoredValue(): String = when (this) {
        VIDEO_ONLY -> "video_only"
        VIDEO_AND_SOUND -> "video_and_sound"
    }

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): VideoAudioMode = when (value?.lowercase()) {
            "video_and_sound" -> VIDEO_AND_SOUND
            else -> VIDEO_ONLY
        }
    }
}
