package com.dogan

/** Sensitivity for jolt and sound detection. */
enum class SensitivityLevel(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CUSTOM("Custom");

    fun toStoredValue(): String = name.lowercase()

    companion object {
        val all = entries.toList()

        fun fromStoredValue(value: String?): SensitivityLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.toStoredValue() == value?.lowercase() }
                ?: MEDIUM
    }
}
