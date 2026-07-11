package com.dogan

/** Alert sound playback duration in seconds. */
enum class AlertDuration(val seconds: Int, val displayName: String) {
    ONE(1, "1 second"),
    TWO(2, "2 seconds"),
    THREE(3, "3 seconds"),
    FOUR(4, "4 seconds"),
    FIVE(5, "5 seconds");

    fun toStoredValue(): String = seconds.toString()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): AlertDuration {
            val sec = value?.toIntOrNull()
            return entries.firstOrNull { it.seconds == sec } ?: THREE
        }
    }
}
