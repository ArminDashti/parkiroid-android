package com.dogan

/** Log sections used to filter section Screens vs the main Logs view. */
enum class LogSection(val storedValue: String) {
    SPOTTER("spotter"),
    WATCHMAN("watchman"),
    RECORDING("recording"),
    CONNECTIVITY("connectivity"),
    GENERAL("general");

    companion object {
        fun fromStoredValue(value: String?): LogSection =
            entries.firstOrNull { it.storedValue.equals(value, ignoreCase = true) }
                ?: GENERAL

        fun forOperatingMode(mode: OperatingMode): LogSection = when (mode) {
            OperatingMode.SPOTTER -> SPOTTER
            OperatingMode.WATCHER -> WATCHMAN
            OperatingMode.COPILOT -> GENERAL
            OperatingMode.OFF -> GENERAL
        }
    }
}
