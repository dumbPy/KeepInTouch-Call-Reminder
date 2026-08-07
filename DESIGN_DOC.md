# KeepInTouch: Smart Call Reminder & Relationship Manager — Design Document

## 1. Executive Summary & Vision

**KeepInTouch** is a modern, privacy-focused Android application designed to help users maintain meaningful personal and professional relationships. Unlike standard calendars with rigid alerts, KeepInTouch uses dynamic, cadence-based reminder algorithms powered by actual communications. By integrating directly with the device's system call logs, the application automatically registers contact touchpoints, calculations of due dates, and relative priorities.

### Core Value Propositions
- **Automated Communication Sync:** Automatically scans local phone call logs (both incoming and outgoing) to record interactions and reset contact timers without requiring manual user input.
- **Group-Based Cadence Management:** Users organize contacts into specific groups (e.g., *Family*, *Close Friends*, *Work*). Each group establishes a shared default reminder frequency and priority.
- **Granular Custom Overrides:** Direct, granular control over specific contacts allows users to set a custom snooze duration, custom priority overrides, or specific day-interval overrides.
- **Safety Deletion Shield:** Group deletions are protected by a timed confirmation modal featuring a progress animation and a 5-second countdown to prevent accidental operations on important contact lists.
- **Visual-First Layouts:** Leverages Material Design 3 elements, employing colorful status pills, elegant lists, and custom counters to simplify relationship tracking.

---

## 2. Technical Feasibility & Android Platform Analysis

### 2.1 Call Tracking & System Permissions
1. **Standard Phone Calls (`READ_CALL_LOG`):**
   - **Mechanism:** A background `WorkManager` job periodically queries `android.provider.CallLog.Calls` for recent call entries matching tracked contact phone numbers.
   - **Real-time Detection:** A `BroadcastReceiver` listens to `TelephonyManager.ACTION_PHONE_STATE_CHANGED` combined with a `ContentObserver` on the CallLog URI to trigger immediate sync when a call completes.
   - **Interaction Filter:** Only completed calls (duration > 0 seconds) reset contact timers. Missed or rejected calls are logged as unfulfilled touchpoints without resetting cadence unless configured.

2. **Third-Party Messaging & VoIP (WhatsApp, Telegram, Signal):**
   - **Android Constraints:** Standard call logs do not capture third-party VoIP call durations or chat timestamps due to sandbox boundaries.
   - **Extensible Architecture Solution:** Interactions are modeled through a generic `InteractionLog` table with `InteractionType` (e.g., `PHONE_CALL`, `WHATSAPP_CALL`, `MANUAL_LOG`). While direct WhatsApp tracking requires an optional `NotificationListenerService`, the app provides a quick 1-tap "Logged WhatsApp Call" action button for seamless manual tracking.

### 2.2 Notifications vs In-App Daily Agenda
1. **Android System Notification Constraints:**
   - Android standard `NotificationCompat` supports custom action buttons (e.g., *Call Now*, *Snooze 1 Day*, *Mark Done*), but **does not support full-width swipe gestures directly inside the notification shade**.
2. **Hybrid Notification Strategy:**
   - **Summary System Notification:** Delivers a daily concise notification at the user's scheduled time/day (e.g., *"3 contacts to call today"*).
   - **Rich In-App Daily Agenda:** Tapping the notification opens the **Daily Agenda Screen**, featuring full interactive Compose swipe gestures:
     - **Right Swipe:** Quick 1-Day Snooze.
     - **Left Swipe:** Opens custom Snooze Picker (3 days, 1 week, custom date) or Tag-based quick snooze.
     - **Tap / Quick Call:** Initiates phone dialer and opens quick interaction logger.

---

## 3. Data Architecture & Database Schema

The database uses **Room (SQLite)** structured around modern clean architecture principles.

### 3.1 Entity Model

```
+------------------+         +--------------------------+         +---------------------+
|   ContactEntity  | 1     * |  ContactTagCrossRef      | *     1 |     GroupEntity     |
+------------------+---------+--------------------------+---------+---------------------+
| id (PK)          |         | contactId (FK)           |         | id (PK)             |
| name             |         | tagId (FK)               |         | name                |
| phoneNumber      |         +--------------------------+         | defaultFreqDays     |
| avatarUri        |                                              | defaultPriority     |
| notes            |         +--------------------------+         | colorHex            |
| createdAt        | 1     * |   InteractionLogEntity   |         +---------------------+
| groupId (FK)     |---------| id (PK)                  |
+------------------+         | contactId (FK)           |
                             | timestamp                |
                             | type (CALL_IN/OUT/MANUAL)|
                             | durationSeconds          |
                             | notes                    |
                             +--------------------------+
```

### 3.2 Database Entities

#### GroupEntity Schema
Defines default cadences and priorities for collections of contacts:
```kotlin
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultFrequencyDays: Int, // Group-level reminder frequency in days
    val defaultPriority: Int, // Group-level priority: 1 = Low, 2 = Normal, 3 = High
    val colorHex: String = "#2196F3"
)
```

#### ContactEntity Schema
Supports global group assignment along with localized item overrides:
```kotlin
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemContactId: Long? = null,
    val lookupKey: String? = null,
    val name: String,
    val phoneNumber: String,
    val secondaryNumbers: String? = null,
    val avatarUri: String? = null,
    val notes: String? = null,
    val lastCalledTimestamp: Long? = null,
    val snoozedUntilTimestamp: Long? = null,
    val customFrequencyDays: Int? = null, // Overrides Group-level default
    val customPriority: Int? = null, // Overrides Group-level default: 1=Low, 2=Normal, 3=High
    val groupId: Long? = null, // Foreign Key relation to GroupEntity
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 4. Cadence Resolution & Algorithms

To calculate when a contact is due and their urgency rank:

### 4.1 Frequency and Priority Resolution
1. **Resolved Reminder Frequency:**
   - If the contact has a `customFrequencyDays` override, use that value.
   - If no override exists but the contact belongs to a Group (`groupId != null`), look up the group's `defaultFrequencyDays`.
   - If neither exists, default to **14 days**.

2. **Resolved Priority:**
   - If the contact has a `customPriority` override, use that value (1 = Low, 2 = Normal, 3 = High).
   - If no override exists but the contact belongs to a Group, look up the group's `defaultPriority`.
   - If neither exists, default to **2 (Normal)**.

### 4.2 Next Contact Date Calculation
1. Locate the latest `InteractionLogEntity` of communication type (`INCOMING_CALL`, `OUTGOING_CALL`, or `MANUAL_LOG`).
2. Locate the active `snoozedUntilTimestamp` of the contact.
3. **Effective Reference Date** = `max(latestInteractionTimestamp, snoozedUntilTimestamp, creationTimestamp)`.
4. **Next Due Date** = `Effective Reference Date + Resolved Frequency Days (converted to milliseconds)`.

---

## 5. Screen & UI Flow Architecture

```
                       +------------------------+
                       |    Main Dashboard      |
                       |  (Daily Agenda Tab)    |
                       +-----------+------------+
                                   |
         +-------------------------+-------------------------+
         |                         |                         |
+--------v-------+        +--------v-------+        +--------v-------+
|  Contacts List |        |   Groups       |        | Contact Detail |
|  & Call Logs   |        |   Management   |        |   & History    |
+----------------+        +----------------+        +----------------+
```

### 5.1 Main Dashboard & Daily Agenda
- Actionable list displaying contacts due for communication.
- Integrates quick-interaction controls (log call, log WhatsApp, snooze 1-day).
- Full customizable snooze durations:
  - Redesigned custom snooze duration interface featuring standalone **Months** and **Days** counters with touch-friendly `-` and `+` buttons to ensure clean grid alignment across various device form factors.

### 5.2 Group Management Screen
- Displays existing groups in a card-based grid layout.
- Group statistics: Shows member counts, priority indicators, and frequency settings.
- **Pill-Based UI Elements:** Displays default frequency and default priority as beautifully colored horizontal Material 3 Pills inside each group card, freeing up visual screen real estate.
- **Delayed Safety Shield (Group Deletion):**
  - Group deletion is safeguarded by an animated confirmation dialog.
  - The confirm button is disabled for **5 seconds** upon opening.
  - A red **LinearProgressIndicator** moves from left to right, visualizing the lock timer.
  - Displays remaining countdown (e.g., `3.5s`) for responsive user feedback, preventing accidental data loss.

### 5.3 Contact Detail Screen
- Interactive detail panel with complete communication histories and timelines.
- Reminders configurations are managed via a streamlined **Configure Reminders** modal:
  - Overrides are selected instantly via horizontal **Priority Pills** (*Default*, *Low*, *Normal*, *High*) arranged in 1 line, avoiding vertical crowding.
  - Contains an intuitive custom frequency stepper, using increment/decrement buttons and state-aware border colors.

---

## 6. Technical Stack & Libraries
- **Framework:** Jetpack Compose (Material Design 3)
- **Database Persistence:** Room Database + KSP Compiler
- **Asynchronous Flow:** Kotlin Coroutines & StateFlow
- **Verification Suite:** Robolectric for local JVM execution & Roborazzi for automated screenshot assertions

---
*This design document serves as the updated source of truth for the KeepInTouch architecture.*
