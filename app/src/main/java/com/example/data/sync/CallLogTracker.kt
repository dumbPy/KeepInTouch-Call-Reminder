package com.example.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.TagDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncResult(
    val importedContactsCount: Int = 0,
    val newCallLogsCount: Int = 0
)

class CallLogTracker(
    private val context: Context,
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    private val interactionLogDao: InteractionLogDao
) {
    /**
     * Performs full synchronization: imports missing system contacts from phonebook
     * and syncs recent call logs.
     */
    suspend fun syncContactsAndCallLogs(): SyncResult = withContext(Dispatchers.IO) {
        val imported = importSystemContacts()
        val newLogs = syncCallLogsWithDatabase()
        SyncResult(importedContactsCount = imported, newCallLogsCount = newLogs)
    }
    /**
     * Normalizes phone number strings for matching.
     */
    fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }

    /**
     * Checks if two phone number strings match flexibly (exact or matching last 7+ digits).
     */
    fun isPhoneMatch(num1: String, num2: String): Boolean {
        val norm1 = normalizePhoneNumber(num1)
        val norm2 = normalizePhoneNumber(num2)
        if (norm1.isEmpty() || norm2.isEmpty()) return false
        if (norm1 == norm2) return true
        val tail1 = if (norm1.length >= 7) norm1.takeLast(7) else norm1
        val tail2 = if (norm2.length >= 7) norm2.takeLast(7) else norm2
        return tail1 == tail2
    }

    /**
     * Imports system contacts from phonebook into Room if READ_CONTACTS permission is granted.
     */
    suspend fun importSystemContacts(): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext 0
        }

        var imported = 0
        val allExisting = contactDao.getAllContactsWithDetails()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numberIdx) ?: continue
                val normalized = normalizePhoneNumber(rawNumber)
                if (normalized.isBlank()) continue

                val exists = allExisting.any { c -> isPhoneMatch(c.contact.phoneNumber, rawNumber) }
                if (!exists) {
                    contactDao.insertContact(
                        ContactEntity(
                            name = name,
                            phoneNumber = rawNumber,
                            notes = "Imported from System Contacts",
                            lastCalledTimestamp = null
                        )
                    )
                    imported++
                }
            }
        }
        return@withContext imported
    }

    /**
     * Removes sample seed contacts (+1 555-010x numbers).
     */
    suspend fun removeSampleContacts(): Int = withContext(Dispatchers.IO) {
        val allContacts = contactDao.getAllContactsWithDetails()
        var deleted = 0
        for (c in allContacts) {
            if (c.contact.phoneNumber.contains("555-010") || c.contact.phoneNumber.startsWith("+1 555")) {
                contactDao.deleteContact(c.contact)
                deleted++
            }
        }
        return@withContext deleted
    }

    /**
     * Scans system CallLog for recent calls matching tracked contact phone numbers.
     * Inserts new interaction logs and updates lastCalledTimestamp.
     */
    suspend fun syncCallLogsWithDatabase(): Int = withContext(Dispatchers.IO) {
        // First import any missing system contacts if READ_CONTACTS is granted
        importSystemContacts()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext 0
        }

        var newLogsAdded = 0
        val allContactsWithDetails = contactDao.getAllContactsWithDetails()

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

            while (it.moveToNext()) {
                val rawNumber = it.getString(numberIdx) ?: continue
                val callTime = it.getLong(dateIdx)
                val duration = it.getLong(durationIdx)
                val callType = it.getInt(typeIdx)

                // Find matching contact in database
                val contactDetails = allContactsWithDetails.firstOrNull { c ->
                    isPhoneMatch(c.contact.phoneNumber, rawNumber)
                } ?: continue

                val contact = contactDetails.contact

                // Map Android CallLog type to InteractionType
                val interactionType = when (callType) {
                    CallLog.Calls.INCOMING_TYPE -> InteractionType.INCOMING_CALL
                    CallLog.Calls.OUTGOING_TYPE -> InteractionType.OUTGOING_CALL
                    else -> null
                } ?: continue

                // Check if this log entry already exists
                val existingLogs = interactionLogDao.getLogsForContact(contact.id)
                val alreadyLogged = existingLogs.any { log ->
                    log.timestamp == callTime && log.type == interactionType
                }

                if (!alreadyLogged) {
                    interactionLogDao.insertLog(
                        InteractionLogEntity(
                            contactId = contact.id,
                            timestamp = callTime,
                            type = interactionType,
                            durationSeconds = duration,
                            note = "Auto-synced from Call Log"
                        )
                    )
                    newLogsAdded++

                    // Update contact lastCalledTimestamp if newer
                    if (contact.lastCalledTimestamp == null || callTime > contact.lastCalledTimestamp) {
                        contactDao.updateContact(
                            contact.copy(
                                lastCalledTimestamp = callTime,
                                snoozedUntilTimestamp = null // Reset snooze when called!
                            )
                        )
                    }
                }
            }
        }
        return@withContext newLogsAdded
    }

    /**
     * Pre-populates default tags and sample contacts if database is empty.
     */
    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        // Clean up any duplicate tags in database
        tagDao.deduplicateTags()

        val existingTags = tagDao.getAllTags()
        if (existingTags.isNotEmpty()) {
            return@withContext
        }

        // Seed Tags
        val defaultTags = listOf(
            TagEntity(name = "Family", category = TagCategory.GROUPING, singleValue = "Family", colorHex = "#2196F3"),
            TagEntity(name = "Close Friends", category = TagCategory.GROUPING, singleValue = "Close Friends", colorHex = "#4CAF50"),
            TagEntity(name = "Work & Network", category = TagCategory.GROUPING, singleValue = "Work", colorHex = "#FF9800"),
            
            TagEntity(name = "Weekly", category = TagCategory.FREQUENCY, singleValue = "7", colorHex = "#9C27B0"),
            TagEntity(name = "Bi-Weekly", category = TagCategory.FREQUENCY, singleValue = "14", colorHex = "#673AB7"),
            TagEntity(name = "Monthly", category = TagCategory.FREQUENCY, singleValue = "30", colorHex = "#3F51B5"),
            
            TagEntity(name = "Snooze 1 Day", category = TagCategory.SNOOZE_DEFAULT, singleValue = "1", colorHex = "#009688"),
            TagEntity(name = "Snooze 3 Days", category = TagCategory.SNOOZE_DEFAULT, singleValue = "3", colorHex = "#00BCD4"),
            
            TagEntity(name = "High Priority", category = TagCategory.PRIORITY, singleValue = "10", colorHex = "#E91E63")
        )

        val insertedTagIds = mutableListOf<Long>()
        for (tag in defaultTags) {
            val id = tagDao.insertTag(tag)
            insertedTagIds.add(id)
        }

        // Seed initial sample contacts if contacts database is empty
        val familyTagId = insertedTagIds.getOrNull(0) ?: 1L
        val friendsTagId = insertedTagIds.getOrNull(1) ?: 2L
        val weeklyTagId = insertedTagIds.getOrNull(3) ?: 4L
        val biWeeklyTagId = insertedTagIds.getOrNull(4) ?: 5L
        val monthlyTagId = insertedTagIds.getOrNull(5) ?: 6L
        val highPriorityTagId = insertedTagIds.getOrNull(8) ?: 9L

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val sampleContacts = listOf(
            ContactEntity(
                name = "Mom (Sarah)",
                phoneNumber = "+1 555-0101",
                notes = "Always call around 7 PM",
                lastCalledTimestamp = now - (9 * dayMs) // Overdue (9 days ago, weekly tag)
            ),
            ContactEntity(
                name = "Alex Johnson",
                phoneNumber = "+1 555-0102",
                notes = "College friend, lives in NYC",
                lastCalledTimestamp = now - (16 * dayMs) // Overdue (16 days ago, biweekly tag)
            ),
            ContactEntity(
                name = "Uncle Robert",
                phoneNumber = "+1 555-0103",
                notes = "Check in about weekend plans",
                lastCalledTimestamp = now - (2 * dayMs) // Up to date
            ),
            ContactEntity(
                name = "Grandma Rose",
                phoneNumber = "+1 555-0104",
                notes = "Loves hearing about updates",
                lastCalledTimestamp = now - (8 * dayMs) // Overdue
            )
        )

        for ((index, contact) in sampleContacts.withIndex()) {
            val contactId = contactDao.insertContact(contact)
            when (index) {
                0 -> {
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, familyTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, weeklyTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, highPriorityTagId))
                    // Add historical interaction log
                    interactionLogDao.insertLog(
                        InteractionLogEntity(
                            contactId = contactId,
                            timestamp = now - (9 * dayMs),
                            type = InteractionType.OUTGOING_CALL,
                            durationSeconds = 420,
                            note = "Caught up on weekly news"
                        )
                    )
                }
                1 -> {
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, friendsTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, biWeeklyTagId))
                    interactionLogDao.insertLog(
                        InteractionLogEntity(
                            contactId = contactId,
                            timestamp = now - (16 * dayMs),
                            type = InteractionType.INCOMING_CALL,
                            durationSeconds = 180,
                            note = "Quick birthday wish"
                        )
                    )
                }
                2 -> {
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, familyTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, monthlyTagId))
                    interactionLogDao.insertLog(
                        InteractionLogEntity(
                            contactId = contactId,
                            timestamp = now - (2 * dayMs),
                            type = InteractionType.MANUAL_LOG,
                            durationSeconds = 0,
                            note = "Messaged on WhatsApp"
                        )
                    )
                }
                3 -> {
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, familyTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, weeklyTagId))
                    contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, highPriorityTagId))
                }
            }
        }
    }
}
