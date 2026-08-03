package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: TagCategory,
    val singleValue: String, // Value according to category: e.g., "7" for FREQUENCY, "1" for SNOOZE_DEFAULT, etc.
    val colorHex: String
)
