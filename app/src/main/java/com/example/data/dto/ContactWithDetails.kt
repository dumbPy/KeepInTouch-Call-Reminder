package com.example.data.dto

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.data.model.ContactEntity
import com.example.data.model.ContactTagCrossRef
import com.example.data.model.InteractionLogEntity
import com.example.data.model.TagEntity
import com.example.data.model.TagCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ContactWithDetails(
    @Embedded val contact: ContactEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ContactTagCrossRef::class,
            parentColumn = "contactId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val interactionLogs: List<InteractionLogEntity>
) {
    /**
     * Resolves effective recurrence frequency in days for this contact.
     * Looks for FREQUENCY tag; if none, uses customFrequencyDays, or defaults to 14 days.
     */
    fun resolvedFrequencyDays(): Int {
        val freqTag = tags.firstOrNull { it.category == TagCategory.FREQUENCY }
        if (freqTag != null) {
            val parsed = freqTag.singleValue.toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        return contact.customFrequencyDays ?: 14
    }

    /**
     * Resolves default swipe snooze duration in days.
     * Looks for SNOOZE_DEFAULT tag; if none, defaults to 1 day.
     */
    fun resolvedDefaultSnoozeDays(): Int {
        val snoozeTag = tags.firstOrNull { it.category == TagCategory.SNOOZE_DEFAULT }
        if (snoozeTag != null) {
            val parsed = snoozeTag.singleValue.toIntOrNull()
            if (parsed != null && parsed > 0) return parsed
        }
        return 1
    }

    /**
     * Gets latest actual touchpoint call timestamp (from logs or contact cached field).
     */
    fun latestTouchpointTimestamp(): Long? {
        val latestLogTime = interactionLogs
            .filter { it.type.isCallTouchpoint }
            .maxOfOrNull { it.timestamp }
        return when {
            latestLogTime != null && contact.lastCalledTimestamp != null -> maxOf(latestLogTime, contact.lastCalledTimestamp)
            latestLogTime != null -> latestLogTime
            else -> contact.lastCalledTimestamp
        }
    }

    /**
     * Standard due timestamp based strictly on last call date (or creation) and frequency.
     */
    fun standardDueTimestamp(): Long {
        val effectiveLastTime = latestTouchpointTimestamp() ?: contact.createdAt
        val freqDays = resolvedFrequencyDays()
        return effectiveLastTime + (freqDays * 86_400_000L)
    }

    /**
     * Standard days until due relative to now (ignoring snooze).
     */
    fun standardDaysUntilDue(): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dueLocalDate = Instant.ofEpochMilli(standardDueTimestamp()).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, dueLocalDate).toInt()
    }

    /**
     * Effective due timestamp taking into account frequency and snooze.
     */
    fun effectiveDueTimestamp(): Long {
        val stdDue = standardDueTimestamp()
        return if (isSnoozed() && contact.snoozedUntilTimestamp != null) {
            contact.snoozedUntilTimestamp
        } else {
            stdDue
        }
    }

    /**
     * Calculates days overdue or remaining. Negative means overdue, positive means due in X days.
     */
    fun daysUntilDue(): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dueLocalDate = Instant.ofEpochMilli(effectiveDueTimestamp()).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, dueLocalDate).toInt()
    }

    /**
     * Number of days added by snooze beyond maxOf(now, standardDueTimestamp).
     */
    fun addedSnoozeDays(): Int {
        if (!isSnoozed() || contact.snoozedUntilTimestamp == null) return 0
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val snoozeLocalDate = Instant.ofEpochMilli(contact.snoozedUntilTimestamp).atZone(zone).toLocalDate()
        val diff = ChronoUnit.DAYS.between(today, snoozeLocalDate).toInt()
        return if (diff > 0) diff else 1
    }

    fun isSnoozed(): Boolean {
        return contact.snoozedUntilTimestamp != null && contact.snoozedUntilTimestamp > System.currentTimeMillis()
    }

    fun priorityWeight(): Int {
        val priorityTag = tags.firstOrNull { it.category == TagCategory.PRIORITY }
        if (priorityTag != null) {
            val parsed = priorityTag.singleValue.toIntOrNull()
            if (parsed != null && parsed in 1..10) return parsed
        }
        return 0
    }

    fun isOverdue(): Boolean {
        if (isSnoozed()) {
            return false
        }
        return daysUntilDue() <= 0
    }
}
