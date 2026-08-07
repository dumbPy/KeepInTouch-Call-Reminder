package com.example.data.dto

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.data.model.ContactEntity
import com.example.data.model.InteractionLogEntity
import com.example.data.model.InteractionType
import com.example.data.model.GroupEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ContactWithDetails(
    @Embedded val contact: ContactEntity,
    
    @Relation(
        parentColumn = "groupId",
        entityColumn = "id"
    )
    val group: GroupEntity?,

    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val interactionLogs: List<InteractionLogEntity>
) {
    /**
     * Resolves effective recurrence frequency in days for this contact.
     * Returns null if no custom frequency and no group frequency is set.
     */
    fun resolvedFrequencyDays(): Int? {
        return contact.customFrequencyDays ?: group?.defaultFrequencyDays
    }

    fun hasFrequencyTracked(): Boolean {
        return resolvedFrequencyDays() != null
    }

    /**
     * Gets latest actual touchpoint call timestamp (from logs or contact cached field).
     * ONLY connected calls (durationSeconds > 0 or manual/WhatsApp touchpoints) count.
     */
    fun latestTouchpointTimestamp(): Long? {
        val callLogs = interactionLogs.filter { it.type.isCallTouchpoint }
        val connectedLogTime = callLogs
            .filter { it.durationSeconds > 0 || it.type == InteractionType.MANUAL_LOG || it.type == InteractionType.WHATSAPP_CALL }
            .maxOfOrNull { it.timestamp }

        if (callLogs.isNotEmpty()) {
            return connectedLogTime
        }

        return connectedLogTime ?: contact.lastCalledTimestamp
    }

    /**
     * Standard due timestamp based strictly on last call date (or creation) and frequency.
     */
    fun standardDueTimestamp(): Long {
        val effectiveLastTime = latestTouchpointTimestamp() ?: contact.createdAt
        val freqDays = resolvedFrequencyDays() ?: 14
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

    /**
     * Resolves effective priority for this contact.
     * Priority: 1 = Low, 2 = Normal, 3 = High.
     */
    fun resolvedPriority(): Int {
        return contact.customPriority ?: group?.defaultPriority ?: 2
    }

    fun priorityWeight(): Int {
        return resolvedPriority()
    }

    fun isOverdue(): Boolean {
        if (isSnoozed()) {
            return false
        }
        return daysUntilDue() <= 0
    }
}
