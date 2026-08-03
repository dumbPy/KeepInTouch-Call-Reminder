package com.example.data.repository

import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.TagDao
import com.example.data.dto.ContactWithDetails
import com.example.data.model.*
import com.example.data.sync.BackupManager
import com.example.data.sync.CallLogTracker
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    private val interactionLogDao: InteractionLogDao,
    private val callLogTracker: CallLogTracker,
    private val backupManager: BackupManager
) {
    val allContactsWithDetails: Flow<List<ContactWithDetails>> =
        contactDao.getAllContactsWithDetailsFlow()

    val allTags: Flow<List<TagEntity>> =
        tagDao.getAllTagsFlow()

    fun getContactDetailsFlow(id: Long): Flow<ContactWithDetails?> =
        contactDao.getContactWithDetailsFlow(id)

    fun getContactLogsFlow(contactId: Long): Flow<List<InteractionLogEntity>> =
        interactionLogDao.getLogsForContactFlow(contactId)

    suspend fun addContact(contact: ContactEntity, tagIds: List<Long>): Long {
        val contactId = contactDao.insertContact(contact)
        for (tagId in tagIds) {
            contactDao.insertContactTagCrossRef(ContactTagCrossRef(contactId, tagId))
        }
        return contactId
    }

    suspend fun updateContact(contact: ContactEntity, tagIds: List<Long>) {
        contactDao.updateContact(contact)
        contactDao.removeAllTagsForContact(contact.id)
        for (tagId in tagIds) {
            contactDao.insertContactTagCrossRef(ContactTagCrossRef(contact.id, tagId))
        }
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
        if (type.isCallTouchpoint) {
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

    suspend fun addTag(tag: TagEntity): Long = tagDao.insertTag(tag)

    suspend fun updateTag(tag: TagEntity) = tagDao.updateTag(tag)

    suspend fun deleteTag(tag: TagEntity) = tagDao.deleteTag(tag)

    suspend fun updateContactsForTag(tagId: Long, selectedContactIds: List<Long>) {
        val targetTag = tagDao.getTagById(tagId) ?: return
        val allContactsWithDetails = contactDao.getAllContactsWithDetails()

        for (item in allContactsWithDetails) {
            val contactId = item.contact.id
            val hasTag = item.tags.any { it.id == tagId }
            val shouldHaveTag = selectedContactIds.contains(contactId)

            if (hasTag != shouldHaveTag) {
                val currentTagIds = item.tags.map { it.id }.toMutableList()
                if (shouldHaveTag) {
                    if (targetTag.category != TagCategory.GROUPING) {
                        val sameCategoryTags = item.tags.filter { it.category == targetTag.category }
                        currentTagIds.removeAll(sameCategoryTags.map { it.id })
                    }
                    if (!currentTagIds.contains(tagId)) {
                        currentTagIds.add(tagId)
                    }
                } else {
                    currentTagIds.remove(tagId)
                }
                updateContact(item.contact, currentTagIds)
            }
        }
    }

    suspend fun syncCallLogs(): Int = callLogTracker.syncCallLogsWithDatabase()

    suspend fun syncContactsAndCallLogs(): com.example.data.sync.SyncResult = callLogTracker.syncContactsAndCallLogs()

    suspend fun exportDataToJson(): String = backupManager.exportDataToJson()

    suspend fun importDataFromJson(jsonString: String): Pair<Int, Int> = backupManager.importDataFromJson(jsonString)

    suspend fun removeSampleContacts(): Int = callLogTracker.removeSampleContacts()

    suspend fun seedDefaultDataIfEmpty() = callLogTracker.seedDefaultDataIfEmpty()
}
