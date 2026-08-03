package com.example.data.sync

import android.content.Context
import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.TagDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    private val interactionLogDao: InteractionLogDao
) {
    /**
     * Exports all local database state (tags, contacts, tag cross-refs, interaction history)
     * as a clean JSON formatted string for backup.
     */
    suspend fun exportDataToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // 1. Tags
        val tags = tagDao.getAllTags()
        val tagsArray = JSONArray()
        tags.forEach { tag ->
            val tagObj = JSONObject().apply {
                put("id", tag.id)
                put("category", tag.category.name)
                put("name", tag.name)
                put("singleValue", tag.singleValue)
                put("colorHex", tag.colorHex)
            }
            tagsArray.put(tagObj)
        }
        root.put("tags", tagsArray)

        // 2. Contacts & Tags mapping
        val contactsWithDetails = contactDao.getAllContactsWithDetails()
        val contactsArray = JSONArray()
        contactsWithDetails.forEach { item ->
            val c = item.contact
            val contactObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phoneNumber", c.phoneNumber)
                put("notes", c.notes ?: "")
                put("avatarUri", c.avatarUri ?: "")
                put("customFrequencyDays", c.customFrequencyDays ?: -1)
                put("lastCalledTimestamp", c.lastCalledTimestamp ?: -1)
                put("snoozedUntilTimestamp", c.snoozedUntilTimestamp ?: -1)
                put("createdAt", c.createdAt)

                // Tag IDs assigned to this contact
                val assignedTagIds = JSONArray()
                item.tags.forEach { assignedTagIds.put(it.id) }
                put("assignedTagIds", assignedTagIds)
            }
            contactsArray.put(contactObj)
        }
        root.put("contacts", contactsArray)

        // 3. Interaction Logs
        val logs = interactionLogDao.getAllLogs()
        val logsArray = JSONArray()
        logs.forEach { log ->
            val logObj = JSONObject().apply {
                put("id", log.id)
                put("contactId", log.contactId)
                put("timestamp", log.timestamp)
                put("type", log.type.name)
                put("durationSeconds", log.durationSeconds)
                put("note", log.note ?: "")
            }
            logsArray.put(logObj)
        }
        root.put("logs", logsArray)

        root.toString(2)
    }

    /**
     * Imports data from a JSON backup string into Room DB.
     * Merges non-duplicate tags and contacts without wiping existing user data.
     * Returns Pair(importedContactsCount, importedTagsCount).
     */
    suspend fun importDataFromJson(jsonString: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)
        
        var importedTagsCount = 0
        var importedContactsCount = 0

        // Tag ID mapping: Old Backup Tag ID -> New Room DB Tag ID
        val tagIdMap = mutableMapOf<Long, Long>()

        // 1. Restore / Merge Tags
        val tagsArray = root.optJSONArray("tags") ?: JSONArray()
        for (i in 0 until tagsArray.length()) {
            val tagObj = tagsArray.getJSONObject(i)
            val oldId = tagObj.optLong("id", -1)
            val catName = tagObj.optString("category", TagCategory.GROUPING.name)
            val category = try { TagCategory.valueOf(catName) } catch (e: Exception) { TagCategory.GROUPING }
            val name = tagObj.optString("name", "")
            val singleValue = tagObj.optString("singleValue", "")
            val colorHex = tagObj.optString("colorHex", "#3B82F6")

            if (name.isNotBlank()) {
                val existingTags = tagDao.getAllTags()
                val existing = existingTags.firstOrNull { it.category == category && it.name.equals(name, ignoreCase = true) }
                val targetId = if (existing != null) {
                    existing.id
                } else {
                    val newTag = TagEntity(
                        category = category,
                        name = name,
                        singleValue = singleValue,
                        colorHex = colorHex
                    )
                    val newId = tagDao.insertTag(newTag)
                    importedTagsCount++
                    newId
                }
                if (oldId != -1L) {
                    tagIdMap[oldId] = targetId
                }
            }
        }

        // 2. Restore / Merge Contacts
        val contactsArray = root.optJSONArray("contacts") ?: JSONArray()
        for (i in 0 until contactsArray.length()) {
            val cObj = contactsArray.getJSONObject(i)
            val phone = cObj.optString("phoneNumber", "")
            val name = cObj.optString("name", "Unknown")
            val notes = cObj.optString("notes", "").ifBlank { null }
            val avatarUri = cObj.optString("avatarUri", "").ifBlank { null }
            val customFreq = cObj.optInt("customFrequencyDays", -1).takeIf { it > 0 }
            val lastCalled = cObj.optLong("lastCalledTimestamp", -1).takeIf { it > 0 }
            val snoozedUntil = cObj.optLong("snoozedUntilTimestamp", -1).takeIf { it > 0 }
            val createdAt = cObj.optLong("createdAt", System.currentTimeMillis())

            if (phone.isNotBlank() || name.isNotBlank()) {
                val existing = if (phone.isNotBlank()) contactDao.findByPhoneNumber(phone) else null
                val contactEntity = ContactEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    phoneNumber = phone,
                    avatarUri = avatarUri ?: existing?.avatarUri,
                    notes = notes ?: existing?.notes,
                    lastCalledTimestamp = lastCalled ?: existing?.lastCalledTimestamp,
                    snoozedUntilTimestamp = snoozedUntil ?: existing?.snoozedUntilTimestamp,
                    customFrequencyDays = customFreq ?: existing?.customFrequencyDays,
                    createdAt = createdAt
                )

                val targetContactId = contactDao.insertContact(contactEntity)
                if (existing == null) importedContactsCount++

                // Restore assigned tags
                val assignedOldTagIds = cObj.optJSONArray("assignedTagIds") ?: JSONArray()
                for (j in 0 until assignedOldTagIds.length()) {
                    val oldTagId = assignedOldTagIds.getLong(j)
                    val newTagId = tagIdMap[oldTagId]
                    if (newTagId != null) {
                        contactDao.insertContactTagCrossRef(ContactTagCrossRef(targetContactId, newTagId))
                    }
                }
            }
        }

        Pair(importedContactsCount, importedTagsCount)
    }
}
