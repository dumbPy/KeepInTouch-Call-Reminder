package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val avatarUri: String? = null,
    val notes: String? = null,
    val lastCalledTimestamp: Long? = null, // Cached latest call timestamp
    val snoozedUntilTimestamp: Long? = null, // If snoozed, timestamp until which it is hidden
    val customFrequencyDays: Int? = null, // Fallback if no Frequency tag assigned (default e.g. 14 days)
    val createdAt: Long = System.currentTimeMillis()
)
