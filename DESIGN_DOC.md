# Call Reminder & Contact Relationship Manager — Design Document

## 1. Executive Summary & Vision

**Call Reminder** is a modern, privacy-focused Android application designed to help users maintain meaningful connections with family, friends, and professional contacts. Instead of relying on rigid calendar events, Call Reminder introduces dynamic, cadence-based reminders driven by actual communication activity.

### Core Value Propositions
- **Automated Communication Sync:** Automatically detects phone calls (incoming and outgoing) via Android's Call Log API, automatically updating contact touchpoints without manual entry.
- **Single-Setting Tag System:** A flexible tag model where each tag belongs to a specific category and controls exactly **one** setting (e.g., *Group Membership*, *Recurrence Frequency*, or *Default Snooze Duration*).
- **Comprehensive Interaction History:** Records all historical contact interactions (calls, manual logs, snoozes) in a timeline, allowing future metrics without architectural refactoring.
- **Daily Agenda & Smart Reminders:** Consolidates pending calls into an actionable daily digest screen featuring intuitive swipe gestures for fast snoozing and logging.

---

## 2. Technical Feasibility & Android Platform Analysis

### 2.1 Call Tracking & System Permissions
1. **Standard Phone Calls (`READ_CALL_LOG`):**
   - **Mechanism:** A background `WorkManager` job periodically queries `android.provider.CallLog.Calls` for recent call entries matching tracked contact phone numbers.
   - **Real-time Detection:** A `BroadcastReceiver` listens to `TelephonyManager.ACTION_PHONE_STATE_CHANGED` combined with a `ContentObserver` on the CallLog URI to trigger immediate sync when a call completes.
   - **Interaction Filter:** Only completed calls (duration > 0 seconds) reset contact timers. Missed or rejected calls can be logged as unfulfilled touchpoints without resetting cadence unless configured.

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
|   ContactEntity  | 1     * |  ContactTagCrossRef      | *     1 |      TagEntity      |
+------------------+---------+--------------------------+---------+---------------------+
| id (PK)          |         | contactId (FK)           |         | id (PK)             |
| name             |         | tagId (FK)               |         | name                |
| phoneNumber      |         +--------------------------+         | category (Enum)     |
| avatarUri        |                                              | value (SettingVal)  |
| notes            |         +--------------------------+         | colorHex            |
| createdAt        | 1     * |   InteractionLogEntity   |         +---------------------+
+------------------+---------+--------------------------+
                             | id (PK)                  |
                             | contactId (FK)           |
                             | timestamp                |
                             | type (CALL_IN/OUT/MANUAL)|
                             | durationSeconds          |
                             | notes                    |
                             +--------------------------+
```

### 3.2 Single-Setting Tag Model

Tags are grouped into strict **Tag Categories**. Each Tag controls **one setting parameter** or attribute:

| Tag Category (`TagCategory`) | Single Purpose | Example Tag Name | Setting Value (`tagValue`) | Color Default |
| :--- | :--- | :--- | :--- | :--- |
| **GROUPING** | Categorize contacts visually | `Family`, `Close Friends` | None / Group ID | Blue / Green |
| **FREQUENCY** | Contact cadence setting | `Weekly`, `Bi-Weekly`, `Monthly` | Days integer (`7`, `14`, `30`) | Purple / Amber |
| **SNOOZE_DEFAULT** | Custom swipe-snooze duration | `Snooze 1 Day`, `Snooze 3 Days` | Days integer (`1`, `3`) | Teal |
| **PRIORITY** | Urgency multiplier | `High Priority`, `VIP` | Weight float (`1.5`) | Crimson |

#### Benefits of Single-Setting Decomposition:
- **No Conflict Matrix:** Eliminates setting ambiguities when a contact has multiple tags. A contact can have one `FREQUENCY` tag (e.g. *Weekly*) and two `GROUPING` tags (*Family*, *VIP*).
- **Bulk Updates:** Modifying a Tag's frequency value instantly updates cadence calculations for all associated contacts without modifying individual contact entities.
- **Custom Color Overrides:** Each tag comes with a smart category-default color, but users can customize hex color codes per tag.

---

## 4. Historical Interaction Log Architecture

To ensure long-term analytics (e.g., call frequency trends, missed connections, interaction timelines) without future schema migrations:

### 4.1 InteractionLogEntity Schema
```kotlin
@Entity(
    tableName = "interaction_logs",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contactId"), Index("timestamp")]
)
data class InteractionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val timestamp: Long, // Epoch ms
    val type: InteractionType, // INCOMING_CALL, OUTGOING_CALL, MANUAL_LOG, SNOOZE, WHATSAPP
    val durationSeconds: Long = 0,
    val note: String? = null
)
```

### 4.2 Cadence Resolution Algorithm
When determining if a contact is **Overdue**, **Due Today**, or **Snoozed**:
1. Find the latest `InteractionLogEntity` of type `INCOMING_CALL`, `OUTGOING_CALL`, or `MANUAL_LOG`.
2. Find any active `SNOOZE` interaction log (or contact `snoozedUntilTimestamp`).
3. Effective Last Contact Date = `max(latestInteractionTimestamp, snoozedUntilTimestamp)`.
4. Resolved Frequency Days = `Contact's FREQUENCY tag value` (defaulting to 14 days if no tag assigned).
5. Next Due Date = `Effective Last Contact Date + Resolved Frequency Days`.

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
|  Contacts List |        | Tag & Settings |        | Contact Detail |
|  & Call Logs   |        |   Management   |        |   & History    |
+----------------+        +----------------+        +----------------+
```

### 5.1 Main Dashboard & Daily Agenda
- **Agenda Card Row Gestures:**
  - **Swipe Right:** Instantly snoozes contact for 1 day (visual green feedback).
  - **Swipe Left:** Reveals custom action drawer (3 Days, 1 Week, Pick Date, or Call Now).
  - **Click Phone Icon:** Direct dialer launch + auto-record callback trigger.
- **Header Summary:** Shows total pending calls today, overdue count, and current date.

### 5.2 Tag Management Screen
- Organized by category headers (*Grouping*, *Recurrence Frequency*, *Snooze Defaults*).
- Tag creation dialog allows name, single setting value, and custom color picker.

### 5.3 Contact Detail & Timeline History
- Complete visual history list showing past incoming/outgoing calls and notes.
- Assigned tags chip group with quick add/remove.

---

## 6. Implementation Checklist & Tech Stack

- **UI Framework:** Jetpack Compose (Material Design 3)
- **Local Database:** Room + KSP
- **Background Sync:** WorkManager + BroadcastReceiver
- **Architecture:** MVVM + StateFlow + Clean Repository Pattern
- **Permissions:** `READ_CALL_LOG`, `READ_CONTACTS`, `POST_NOTIFICATIONS`

*This design document serves as the architectural foundation for the Call Reminder application.*
