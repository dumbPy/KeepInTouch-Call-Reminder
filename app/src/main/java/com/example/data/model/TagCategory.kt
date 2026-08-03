package com.example.data.model

enum class TagCategory(
    val displayName: String,
    val description: String,
    val singleSettingLabel: String,
    val defaultColorHex: String
) {
    GROUPING(
        displayName = "Group",
        description = "Collects contacts under a social group (e.g. Family, Work)",
        singleSettingLabel = "Group Label",
        defaultColorHex = "#2196F3" // Blue
    ),
    FREQUENCY(
        displayName = "Frequency",
        description = "Sets reminder recurrence period for associated contacts",
        singleSettingLabel = "Recurrence (Days)",
        defaultColorHex = "#9C27B0" // Purple
    ),
    SNOOZE_DEFAULT(
        displayName = "Snooze Preset",
        description = "Sets default swipe snooze duration for associated contacts",
        singleSettingLabel = "Snooze Duration (Days)",
        defaultColorHex = "#009688" // Teal
    ),
    PRIORITY(
        displayName = "Priority Weight",
        description = "Sets visual priority badge and sorting weight",
        singleSettingLabel = "Priority Level (1-10)",
        defaultColorHex = "#E91E63" // Crimson
    )
}
