package com.dogan

/** Types of alerts that map to server-downloaded sounds. */
enum class AlertType(val soundId: String) {
    BUMP("bump"),
    PERSON("person"),
    SOUND_SPIKE("sound_spike"),
    VEHICLE_DEPARTED("vehicle_departed"),
    INTRUSION("intrusion"),
    OVERSPEED("overspeed"),
    SPEED_CAMERA("speed_camera"),
    GENERIC_WARNING("generic_warning");

    companion object {
        fun fromSoundId(id: String): AlertType? =
            entries.firstOrNull { it.soundId == id }
    }
}
