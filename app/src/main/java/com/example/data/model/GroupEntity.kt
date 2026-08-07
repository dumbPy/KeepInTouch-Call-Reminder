package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultFrequencyDays: Int, // Group-level reminder frequency in days
    val defaultPriority: Int, // Group-level priority: 1 = Low, 2 = Normal, 3 = High
    val colorHex: String = "#2196F3"
)
