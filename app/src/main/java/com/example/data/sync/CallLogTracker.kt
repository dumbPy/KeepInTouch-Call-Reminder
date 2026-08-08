package com.example.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.GroupDao
import com.example.data.dto.ContactWithDetails
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
    private val groupDao: GroupDao,
    private val interactionLogDao: InteractionLogDao
) {
    private val prefs by lazy {
        context.getSharedPreferences("call_log_sync_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Performs full synchronization: imports missing system contacts from phonebook
     * and syncs call logs incrementally.
     */
    suspend fun syncContactsAndCallLogs(): SyncResult = withContext(Dispatchers.IO) {
        val imported = importSystemContacts()
        val newLogs = syncCallLogsIncremental(forceFullScan = false)
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
     * Builds an in-memory map for fast O(1) phone number to ContactEntity lookup.
     */
    private suspend fun buildPhoneLookupMap(): Map<String, ContactEntity> = withContext(Dispatchers.IO) {
        val allContacts = contactDao.getAllContactsWithDetails()
        val map = HashMap<String, ContactEntity>()
        for (c in allContacts) {
            for (num in c.contact.getAllPhoneNumbers()) {
                val normalized = normalizePhoneNumber(num)
                if (normalized.isNotBlank()) {
                    map[normalized] = c.contact
                    if (normalized.length >= 7) {
                        map[normalized.takeLast(7)] = c.contact
                    }
                }
            }
        }
        map
    }

    private fun findContactForNumber(phoneMap: Map<String, ContactEntity>, rawNumber: String): ContactEntity? {
        val norm = normalizePhoneNumber(rawNumber)
        if (norm.isBlank()) return null
        phoneMap[norm]?.let { return it }
        if (norm.length >= 7) {
            phoneMap[norm.takeLast(7)]?.let { return it }
        }
        return null
    }

    /**
     * Deduplicates contacts in database by merging contacts with identical names or matching numbers.
     */
    suspend fun deduplicateContactsInDatabase(): Int = withContext(Dispatchers.IO) {
        val all = contactDao.getAllContactsWithDetails()
        if (all.size < 2) return@withContext 0

        var mergedCount = 0
        // Group contacts by normalized name (lowercase trimmed)
        val nameGroups = all.groupBy { it.contact.name.trim().lowercase() }

        for ((_, group) in nameGroups) {
            if (group.size > 1) {
                val primary = group.minByOrNull { it.contact.id } ?: continue
                val duplicates = group.filter { it.contact.id != primary.contact.id }

                val combinedNumbers = mutableListOf<String>()
                combinedNumbers.addAll(primary.contact.getAllPhoneNumbers())
                duplicates.forEach { dup ->
                    combinedNumbers.addAll(dup.contact.getAllPhoneNumbers())
                }
                val distinctNumbers = combinedNumbers.map { it.trim() }.filter { it.isNotBlank() }.distinct()

                val newPrimaryNumber = distinctNumbers.firstOrNull() ?: primary.contact.phoneNumber
                val newSecondaryNumbers = if (distinctNumbers.size > 1) distinctNumbers.drop(1).joinToString(", ") else null

                val updatedPrimary = primary.contact.copy(
                    phoneNumber = newPrimaryNumber,
                    secondaryNumbers = newSecondaryNumbers
                )
                contactDao.updateContact(updatedPrimary)

                for (dup in duplicates) {
                    contactDao.transferInteractionLogs(dup.contact.id, primary.contact.id)
                    contactDao.deleteContactById(dup.contact.id)
                    mergedCount++
                }
            }
        }
        return@withContext mergedCount
    }

    /**
     * Imports system contacts from phonebook into Room if READ_CONTACTS permission is granted.
     * Groups multiple numbers for the same person under a single contact record with system contact ID & photo URI.
     */
    suspend fun importSystemContacts(): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext 0
        }

        deduplicateContactsInDatabase()
        removeSampleContacts()

        var imported = 0

        data class SystemGroupData(
            val contactId: Long,
            val lookupKey: String?,
            val name: String,
            val photoUri: String?,
            val numbers: MutableList<String> = mutableListOf()
        )

        val systemContactGroups = mutableMapOf<Long, SystemGroupData>()

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            val contactIdIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupKeyIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext()) {
                val cId = if (contactIdIdx != -1) it.getLong(contactIdIdx) else 0L
                val lookupKey = if (lookupKeyIdx != -1) it.getString(lookupKeyIdx) else null
                val name = it.getString(nameIdx) ?: continue
                val rawNumber = it.getString(numberIdx) ?: continue
                val photoUri = if (photoIdx != -1) it.getString(photoIdx) else null

                val normalized = normalizePhoneNumber(rawNumber)
                if (normalized.isBlank()) continue

                val groupKey = if (cId > 0) cId else name.hashCode().toLong()
                val group = systemContactGroups.getOrPut(groupKey) {
                    SystemGroupData(
                        contactId = cId,
                        lookupKey = lookupKey,
                        name = name.trim(),
                        photoUri = photoUri
                    )
                }

                if (!group.numbers.contains(rawNumber.trim())) {
                    group.numbers.add(rawNumber.trim())
                }
            }
        }

        val allExisting = contactDao.getAllContactsWithDetails()
        val lookupMap = mutableMapOf<String, ContactWithDetails>()
        val systemIdMap = mutableMapOf<Long, ContactWithDetails>()
        val nameMap = mutableMapOf<String, ContactWithDetails>()
        val phoneMap = mutableMapOf<String, ContactWithDetails>()

        for (c in allExisting) {
            c.contact.lookupKey?.let { lookupMap[it] = c }
            c.contact.systemContactId?.let { if (it > 0) systemIdMap[it] = c }
            nameMap[c.contact.name.trim().lowercase()] = c
            for (num in c.contact.getAllPhoneNumbers()) {
                val norm = normalizePhoneNumber(num)
                if (norm.isNotBlank()) {
                    phoneMap[norm] = c
                    if (norm.length >= 7) {
                        phoneMap[norm.takeLast(7)] = c
                    }
                }
            }
        }

        for ((_, group) in systemContactGroups) {
            if (group.numbers.isEmpty()) continue

            var existingContact: ContactWithDetails? = null
            if (group.lookupKey != null) {
                existingContact = lookupMap[group.lookupKey]
            }
            if (existingContact == null && group.contactId > 0) {
                existingContact = systemIdMap[group.contactId]
            }
            if (existingContact == null) {
                existingContact = nameMap[group.name.lowercase()]
            }
            if (existingContact == null) {
                for (num in group.numbers) {
                    val norm = normalizePhoneNumber(num)
                    val match = phoneMap[norm] ?: if (norm.length >= 7) phoneMap[norm.takeLast(7)] else null
                    if (match != null) {
                        existingContact = match
                        break
                    }
                }
            }

            if (existingContact != null) {
                val currentNums = existingContact.contact.getAllPhoneNumbers().toMutableList()
                for (num in group.numbers) {
                    if (!currentNums.any { isPhoneMatch(it, num) }) {
                        currentNums.add(num)
                    }
                }
                val sec = if (currentNums.size > 1) currentNums.drop(1).joinToString(", ") else null
                val updatedContact = existingContact.contact.copy(
                    systemContactId = if (group.contactId > 0) group.contactId else existingContact.contact.systemContactId,
                    lookupKey = group.lookupKey ?: existingContact.contact.lookupKey,
                    avatarUri = group.photoUri ?: existingContact.contact.avatarUri,
                    secondaryNumbers = sec
                )
                if (updatedContact != existingContact.contact) {
                    contactDao.updateContact(updatedContact)
                }
            } else {
                val primaryNum = group.numbers.first()
                val secNums = if (group.numbers.size > 1) group.numbers.drop(1).joinToString(", ") else null
                contactDao.insertContact(
                    ContactEntity(
                        systemContactId = if (group.contactId > 0) group.contactId else null,
                        lookupKey = group.lookupKey,
                        name = group.name,
                        phoneNumber = primaryNum,
                        secondaryNumbers = secNums,
                        avatarUri = group.photoUri,
                        notes = "Imported from System Contacts",
                        lastCalledTimestamp = null
                    )
                )
                imported++
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
     * Incrementally scans system CallLog for new calls matching tracked contact phone numbers.
     * Fast, lightweight, and safe to call on every app open/resume.
     * ONLY connected calls (duration > 0) update lastCalledTimestamp and reset due date.
     */
    suspend fun syncCallLogsIncremental(forceFullScan: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext 0
        }

        val lastSyncTime = if (forceFullScan) 0L else prefs.getLong("last_call_log_sync", 0L)
        val now = System.currentTimeMillis()

        // If never synced before, fetch call logs from last 30 days to keep initial scan instant
        val querySinceTime = if (lastSyncTime > 0) lastSyncTime else (now - 30 * 86_400_000L)

        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(querySinceTime.toString())

        val phoneMap = buildPhoneLookupMap()
        if (phoneMap.isEmpty()) return@withContext 0

        var newLogsAdded = 0
        var maxSeenCallTime = lastSyncTime

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            selection,
            selectionArgs,
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

                if (callTime > maxSeenCallTime) {
                    maxSeenCallTime = callTime
                }

                val contact = findContactForNumber(phoneMap, rawNumber) ?: continue

                val interactionType = when (callType) {
                    CallLog.Calls.INCOMING_TYPE -> InteractionType.INCOMING_CALL
                    CallLog.Calls.OUTGOING_TYPE -> InteractionType.OUTGOING_CALL
                    else -> null
                } ?: continue

                val existingLogs = interactionLogDao.getLogsForContact(contact.id)
                val alreadyLogged = existingLogs.any { log ->
                    log.timestamp == callTime && log.type == interactionType
                }

                if (!alreadyLogged) {
                    val isConnectedCall = duration > 0
                    val logNote = if (isConnectedCall) "Auto-synced Call Log (${duration}s)" else "Unanswered Call (${duration}s)"

                    interactionLogDao.insertLog(
                        InteractionLogEntity(
                            contactId = contact.id,
                            timestamp = callTime,
                            type = interactionType,
                            durationSeconds = duration,
                            note = logNote
                        )
                    )
                    newLogsAdded++

                    // ONLY connected calls (duration > 0) count as contact touchpoint and reset counter
                    if (isConnectedCall) {
                        if (contact.lastCalledTimestamp == null || callTime > contact.lastCalledTimestamp) {
                            val matchedNumber = contact.getAllPhoneNumbers().find { num ->
                                isPhoneMatch(num, rawNumber)
                            } ?: rawNumber
                            contactDao.updateContact(
                                contact.copy(
                                    lastCalledTimestamp = callTime,
                                    snoozedUntilTimestamp = null, // Reset snooze on connected call!
                                    mostRecentlyUsedNumber = matchedNumber
                                )
                            )
                        }
                    } else {
                        // Even if not connected, update mostRecentlyUsedNumber if it's the latest log event
                        if (contact.lastCalledTimestamp == null || callTime > contact.lastCalledTimestamp) {
                            val matchedNumber = contact.getAllPhoneNumbers().find { num ->
                                isPhoneMatch(num, rawNumber)
                            } ?: rawNumber
                            contactDao.updateContact(
                                contact.copy(
                                    mostRecentlyUsedNumber = matchedNumber
                                )
                            )
                        }
                    }
                }
            }
        }

        val newSyncPoint = if (maxSeenCallTime > lastSyncTime) maxSeenCallTime else now
        prefs.edit().putLong("last_call_log_sync", newSyncPoint).apply()

        return@withContext newLogsAdded
    }

    suspend fun syncCallLogsWithDatabase(): Int = syncCallLogsIncremental(forceFullScan = false)

    /**
     * Pre-populates default groups and sample contacts if database is empty.
     */
    suspend fun seedDefaultDataIfEmpty() = withContext(Dispatchers.IO) {
        val existingGroups = groupDao.getAllGroups()
        if (existingGroups.isNotEmpty()) {
            return@withContext
        }

        // Seed Groups
        val defaultGroups = listOf(
            GroupEntity(name = "Family", defaultFrequencyDays = 7, defaultPriority = 3, colorHex = "#2196F3"),
            GroupEntity(name = "Close Friends", defaultFrequencyDays = 14, defaultPriority = 2, colorHex = "#4CAF50"),
            GroupEntity(name = "Work", defaultFrequencyDays = 30, defaultPriority = 1, colorHex = "#FF9800")
        )

        val groupIds = mutableListOf<Long>()
        for (group in defaultGroups) {
            val id = groupDao.insertGroup(group)
            groupIds.add(id)
        }

        // Seed initial sample contacts if contacts database is empty
        val familyGroupId = groupIds.getOrNull(0) ?: 1L
        val friendsGroupId = groupIds.getOrNull(1) ?: 2L

        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        val sampleContacts = listOf(
            ContactEntity(
                name = "Mom (Sarah)",
                phoneNumber = "+1 555-0101",
                notes = "Always call around 7 PM",
                lastCalledTimestamp = now - (9 * dayMs), // Overdue (9 days ago, weekly group)
                groupId = familyGroupId
            ),
            ContactEntity(
                name = "Alex Johnson",
                phoneNumber = "+1 555-0102",
                notes = "College friend, lives in NYC",
                lastCalledTimestamp = now - (16 * dayMs), // Overdue (16 days ago, close friends group)
                groupId = friendsGroupId
            ),
            ContactEntity(
                name = "Uncle Robert",
                phoneNumber = "+1 555-0103",
                notes = "Check in about weekend plans",
                lastCalledTimestamp = now - (2 * dayMs), // Up to date
                groupId = familyGroupId
            ),
            ContactEntity(
                name = "Grandma Rose",
                phoneNumber = "+1 555-0104",
                notes = "Loves hearing about updates",
                lastCalledTimestamp = now - (8 * dayMs), // Overdue
                groupId = familyGroupId
            )
        )

        for ((index, contact) in sampleContacts.withIndex()) {
            val contactId = contactDao.insertContact(contact)
            when (index) {
                0 -> {
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
            }
        }
    }
}
