package com.dogan

/** Operational modes that control monitoring behavior. */
enum class OperatingMode(val displayName: String) {
    COPILOT("Copilot"),
    SPOTTER("Spotter"),
    WATCHER("Watchman"),
    OFF("OFF");

    fun toStoredValue(): String = name.lowercase()

    companion object {
        /** Modes shown on the main-screen selector (includes OFF). */
        val all = entries.toList()

        /** Modes that run capture / detection engines. */
        val activeModes = listOf(COPILOT, SPOTTER, WATCHER)

        fun fromStoredValue(value: String?): OperatingMode {
            val normalized = value?.lowercase()?.trim().orEmpty()
            return when (normalized) {
                "off" -> OFF
                "watchman", "watchman_spotter", "watcher" -> WATCHER
                "spotter" -> SPOTTER
                "copilot" -> COPILOT
                else -> entries.firstOrNull {
                    it.name.equals(value, ignoreCase = true) || it.toStoredValue() == normalized
                } ?: OFF
            }
        }
    }
}
