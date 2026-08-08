package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemContactId: Long? = null, // Android ContactsContract Contact ID
    val lookupKey: String? = null, // Android ContactsContract stable Lookup Key
    val name: String,
    val phoneNumber: String, // Primary phone number
    val secondaryNumbers: String? = null, // Comma separated additional phone numbers
    val avatarUri: String? = null,
    val notes: String? = null,
    val lastCalledTimestamp: Long? = null, // Cached latest call timestamp
    val snoozedUntilTimestamp: Long? = null, // If snoozed, timestamp until which it is hidden
    val customFrequencyDays: Int? = null, // Override or custom frequency (in days)
    val customPriority: Int? = null, // Override priority: 1=Low, 2=Normal, 3=High
    val groupId: Long? = null, // Group association
    val mostRecentlyUsedNumber: String? = null, // Track most recently used phone number
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns a deduplicated list of all phone numbers (primary + secondary) associated with this contact.
     */
    fun getAllPhoneNumbers(): List<String> {
        val numbers = mutableListOf<String>()
        if (phoneNumber.isNotBlank()) {
            numbers.add(phoneNumber.trim())
        }
        if (!secondaryNumbers.isNullOrBlank()) {
            secondaryNumbers.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !numbers.contains(it) }
                .forEach { numbers.add(it) }
        }
        return numbers
    }
}
