package com.example.data.sync

import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.GroupDao
import com.example.data.dto.ContactWithDetails
import com.example.data.model.ContactEntity
import com.example.data.model.InteractionLogEntity
import com.example.data.model.GroupEntity
import org.json.JSONArray
import org.json.JSONObject

class BackupManager(
    private val contactDao: ContactDao,
    private val groupDao: GroupDao,
    private val interactionLogDao: InteractionLogDao
) {

    suspend fun exportDataToJson(): String {
        val groups = groupDao.getAllGroups()
        val contacts = contactDao.getAllContactsWithDetails()

        val root = JSONObject()

        // Export Groups
        val groupsArray = JSONArray()
        for (g in groups) {
            val gObj = JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("defaultFrequencyDays", g.defaultFrequencyDays)
                put("defaultPriority", g.defaultPriority)
                put("colorHex", g.colorHex)
            }
            groupsArray.put(gObj)
        }
        root.put("groups", groupsArray)

        // Export Contacts
        val contactsArray = JSONArray()
        for (item in contacts) {
            val cObj = JSONObject().apply {
                put("id", item.contact.id)
                put("systemContactId", item.contact.systemContactId ?: JSONObject.NULL)
                put("lookupKey", item.contact.lookupKey ?: JSONObject.NULL)
                put("name", item.contact.name)
                put("phoneNumber", item.contact.phoneNumber)
                put("secondaryNumbers", item.contact.secondaryNumbers ?: JSONObject.NULL)
                put("avatarUri", item.contact.avatarUri ?: JSONObject.NULL)
                put("notes", item.contact.notes ?: JSONObject.NULL)
                put("lastCalledTimestamp", item.contact.lastCalledTimestamp ?: JSONObject.NULL)
                put("snoozedUntilTimestamp", item.contact.snoozedUntilTimestamp ?: JSONObject.NULL)
                put("customFrequencyDays", item.contact.customFrequencyDays ?: JSONObject.NULL)
                put("customPriority", item.contact.customPriority ?: JSONObject.NULL)
                put("groupId", item.contact.groupId ?: JSONObject.NULL)
                put("createdAt", item.contact.createdAt)
            }

            // Logs for this contact
            val logsArray = JSONArray()
            for (log in item.interactionLogs) {
                val lObj = JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("type", log.type.name)
                    put("durationSeconds", log.durationSeconds)
                    put("note", log.note ?: JSONObject.NULL)
                }
                logsArray.put(lObj)
            }
            cObj.put("logs", logsArray)

            contactsArray.put(cObj)
        }
        root.put("contacts", contactsArray)

        return root.toString(4)
    }

    suspend fun importDataFromJson(jsonString: String): Pair<Int, Int> {
        val root = JSONObject(jsonString)

        val groupMap = mutableMapOf<Long, Long>() // Old ID to New ID mapping

        // Import Groups
        var importedGroupsCount = 0
        if (root.has("groups")) {
            val groupsArray = root.getJSONArray("groups")
            val existingGroups = groupDao.getAllGroups()

            for (i in 0 until groupsArray.length()) {
                val gObj = groupsArray.getJSONObject(i)
                val oldId = gObj.getLong("id")
                val name = gObj.getString("name")
                val freq = gObj.getInt("defaultFrequencyDays")
                val priority = gObj.getInt("defaultPriority")
                val colorHex = gObj.optString("colorHex", "#2196F3")

                // Try to find if duplicate exists
                val duplicate = existingGroups.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val targetId = if (duplicate != null) {
                    duplicate.id
                } else {
                    val newGroup = GroupEntity(
                        name = name,
                        defaultFrequencyDays = freq,
                        defaultPriority = priority,
                        colorHex = colorHex
                    )
                    groupDao.insertGroup(newGroup)
                }
                groupMap[oldId] = targetId
                importedGroupsCount++
            }
        }

        // Import Contacts
        var importedContactsCount = 0
        if (root.has("contacts")) {
            val contactsArray = root.getJSONArray("contacts")
            for (i in 0 until contactsArray.length()) {
                val cObj = contactsArray.getJSONObject(i)
                val name = cObj.getString("name")
                val phone = cObj.getString("phoneNumber")

                // Check for duplicates
                var existingContact = contactDao.findByPhoneNumber(phone)
                if (existingContact == null) {
                    // Match by name
                    existingContact = contactDao.getAllContactsWithDetails()
                        .map { it.contact }
                        .firstOrNull { it.name.equals(name, ignoreCase = true) }
                }

                val oldGroupId = if (cObj.isNull("groupId")) null else cObj.getLong("groupId")
                val resolvedGroupId = oldGroupId?.let { groupMap[it] }

                val contactEntity = ContactEntity(
                    systemContactId = if (cObj.isNull("systemContactId")) null else cObj.getLong("systemContactId"),
                    lookupKey = if (cObj.isNull("lookupKey")) null else cObj.getString("lookupKey"),
                    name = name,
                    phoneNumber = phone,
                    secondaryNumbers = if (cObj.isNull("secondaryNumbers")) null else cObj.getString("secondaryNumbers"),
                    avatarUri = if (cObj.isNull("avatarUri")) null else cObj.getString("avatarUri"),
                    notes = if (cObj.isNull("notes")) null else cObj.getString("notes"),
                    lastCalledTimestamp = if (cObj.isNull("lastCalledTimestamp")) null else cObj.getLong("lastCalledTimestamp"),
                    snoozedUntilTimestamp = if (cObj.isNull("snoozedUntilTimestamp")) null else cObj.getLong("snoozedUntilTimestamp"),
                    customFrequencyDays = if (cObj.isNull("customFrequencyDays")) null else cObj.getInt("customFrequencyDays"),
                    customPriority = if (cObj.isNull("customPriority")) null else cObj.getInt("customPriority"),
                    groupId = resolvedGroupId,
                    createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                )

                val targetContactId = if (existingContact != null) {
                    // Merge / update
                    val updatedContact = contactEntity.copy(
                        id = existingContact.id,
                        groupId = contactEntity.groupId ?: existingContact.groupId,
                        customFrequencyDays = contactEntity.customFrequencyDays ?: existingContact.customFrequencyDays,
                        customPriority = contactEntity.customPriority ?: existingContact.customPriority
                    )
                    contactDao.updateContact(updatedContact)
                    existingContact.id
                } else {
                    contactDao.insertContact(contactEntity)
                }

                // Import logs for this contact
                if (cObj.has("logs")) {
                    val logsArray = cObj.getJSONArray("logs")
                    for (j in 0 until logsArray.length()) {
                        val lObj = logsArray.getJSONObject(j)
                        val timestamp = lObj.getLong("timestamp")
                        val typeStr = lObj.getString("type")
                        val duration = lObj.optLong("durationSeconds", 0L)
                        val note = if (lObj.isNull("note")) null else lObj.getString("note")

                        val type = try {
                            com.example.data.model.InteractionType.valueOf(typeStr)
                        } catch (e: Exception) {
                            com.example.data.model.InteractionType.MANUAL_LOG
                        }

                        // Avoid inserting duplicate logs
                        val existingLogs = interactionLogDao.getLogsForContact(targetContactId)
                        val isDuplicate = existingLogs.any {
                            Math.abs(it.timestamp - timestamp) < 2000 && it.type == type
                        }

                        if (!isDuplicate) {
                            interactionLogDao.insertLog(
                                com.example.data.model.InteractionLogEntity(
                                    contactId = targetContactId,
                                    timestamp = timestamp,
                                    type = type,
                                    durationSeconds = duration,
                                    note = note
                                )
                            )
                        }
                    }
                }
                importedContactsCount++
            }
        }

        return Pair(importedContactsCount, importedGroupsCount)
    }
}
