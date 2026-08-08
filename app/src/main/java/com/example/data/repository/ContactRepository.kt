package com.example.data.repository

import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.GroupDao
import com.example.data.dto.ContactWithDetails
import com.example.data.model.*
import com.example.data.sync.BackupManager
import com.example.data.sync.CallLogTracker
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val contactDao: ContactDao,
    private val groupDao: GroupDao,
    private val interactionLogDao: InteractionLogDao,
    private val callLogTracker: CallLogTracker,
    private val backupManager: BackupManager
) {
    val allContactsWithDetails: Flow<List<ContactWithDetails>> =
        contactDao.getAllContactsWithDetailsFlow()

    val allGroups: Flow<List<GroupEntity>> =
        groupDao.getAllGroupsFlow()

    fun getContactDetailsFlow(id: Long): Flow<ContactWithDetails?> =
        contactDao.getContactWithDetailsFlow(id)

    suspend fun getContactById(id: Long): ContactEntity? =
        contactDao.getContactById(id)

    fun getContactLogsFlow(contactId: Long): Flow<List<InteractionLogEntity>> =
        interactionLogDao.getLogsForContactFlow(contactId)

    suspend fun addContact(contact: ContactEntity): Long {
        return contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: ContactEntity) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: ContactEntity) {
        contactDao.deleteContact(contact)
    }

    suspend fun logCallInteraction(
        contactId: Long,
        type: InteractionType = InteractionType.MANUAL_LOG,
        durationSeconds: Long = 0,
        note: String? = null
    ) {
        val now = System.currentTimeMillis()
        interactionLogDao.insertLog(
            InteractionLogEntity(
                contactId = contactId,
                timestamp = now,
                type = type,
                durationSeconds = durationSeconds,
                note = note
            )
        )

        val contact = contactDao.getContactById(contactId) ?: return
        if (type.isCallTouchpoint && (durationSeconds > 0 || type == InteractionType.MANUAL_LOG || type == InteractionType.WHATSAPP_CALL)) {
            contactDao.updateContact(
                contact.copy(
                    lastCalledTimestamp = now,
                    snoozedUntilTimestamp = null // Reset snooze when touched!
                )
            )
        }
    }

    suspend fun snoozeContact(contactId: Long, snoozeDays: Int) {
        val allDetails = contactDao.getAllContactsWithDetails()
        val item = allDetails.firstOrNull { it.contact.id == contactId } ?: return
        val now = System.currentTimeMillis()
        val baseTime = if (item.isSnoozed() && item.contact.snoozedUntilTimestamp != null) {
            item.contact.snoozedUntilTimestamp
        } else {
            maxOf(now, item.standardDueTimestamp())
        }
        val snoozeUntil = baseTime + (snoozeDays * 86_400_000L)
        contactDao.updateContact(
            item.contact.copy(snoozedUntilTimestamp = snoozeUntil)
        )
        // Log snooze action into timeline history
        interactionLogDao.insertLog(
            InteractionLogEntity(
                contactId = contactId,
                timestamp = now,
                type = InteractionType.SNOOZE,
                durationSeconds = 0,
                note = "Snoozed for $snoozeDays day(s)"
            )
        )
    }

    suspend fun resetSnooze(contactId: Long) {
        val contact = contactDao.getContactById(contactId) ?: return
        contactDao.updateContact(
            contact.copy(snoozedUntilTimestamp = null)
        )
    }

    suspend fun addGroup(group: GroupEntity): Long = groupDao.insertGroup(group)

    suspend fun updateGroup(group: GroupEntity) = groupDao.updateGroup(group)

    suspend fun deleteGroup(group: GroupEntity) = groupDao.deleteGroup(group)

    suspend fun updateContactsForGroup(groupId: Long, selectedContactIds: List<Long>) {
        val allContactsWithDetails = contactDao.getAllContactsWithDetails()

        for (item in allContactsWithDetails) {
            val contactId = item.contact.id
            val inGroup = item.contact.groupId == groupId
            val shouldBeInGroup = selectedContactIds.contains(contactId)

            if (inGroup != shouldBeBeInGroup(inGroup, shouldBeInGroup)) {
                val updatedContact = item.contact.copy(
                    groupId = if (shouldBeInGroup) groupId else null
                )
                contactDao.updateContact(updatedContact)
            }
        }
    }

    private fun shouldBeBeInGroup(inGroup: Boolean, shouldBeInGroup: Boolean): Boolean {
        return shouldBeInGroup
    }

    suspend fun syncCallLogsIncremental(): Int = callLogTracker.syncCallLogsIncremental()

    suspend fun syncCallLogs(): Int = callLogTracker.syncCallLogsIncremental()

    suspend fun syncContactsAndCallLogs(): com.example.data.sync.SyncResult = callLogTracker.syncContactsAndCallLogs()

    suspend fun exportDataToJson(): String = backupManager.exportDataToJson()

    suspend fun importDataFromJson(jsonString: String): Pair<Int, Int> = backupManager.importDataFromJson(jsonString)

    suspend fun removeSampleContacts(): Int = callLogTracker.removeSampleContacts()

    suspend fun seedDefaultDataIfEmpty() = callLogTracker.seedDefaultDataIfEmpty()
}
