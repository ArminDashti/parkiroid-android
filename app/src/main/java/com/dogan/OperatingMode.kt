package com.dogan

/** Operational modes that control monitoring behavior. */
enum class OperatingMode(val displayName: String) {
    WATCHMAN("Watchman"),
    SPOTTER("Spotter"),
    WATCHMAN_SPOTTER("Watchman-Spotter"),
    COPILOT("Copilot");

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): OperatingMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase() }
                ?: WATCHMAN
    }
}
