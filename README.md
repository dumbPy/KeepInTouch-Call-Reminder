# KeepInTouch: Smart Call Reminder & Relationship Manager

**KeepInTouch** is a beautiful, private, and automatic relationship manager for Android. It helps you stay in close contact with the people who matter most—your family, friends, and professional network—without the rigidity of standard calendar alerts. 

By automatically checking your phone's call logs, **KeepInTouch** knows exactly when you last spoke with someone, calculating the best time to remind you to reach out again. It operates completely on your device, ensuring your contact details and call history remain 100% private.

---

## Why KeepInTouch?
* **No Manual Data Entry:** When you make or receive a phone call, KeepInTouch automatically detects it and resets the contact's reminder timer.
* **Cadence, Not Deadlines:** Instead of strict calendar notifications, you organize your contacts into groups with specific communication frequencies (e.g., "Family" every 7 days).
* **Flexible Snoozing:** Too busy to talk right now? Easily snooze reminders using an intuitive touch controller.
* **Privacy-First Design:** Zero external servers, trackers, or cloud sync. All your information resides securely on your own phone.

---

## Interactive Visual Preview (The 4 Tabs)

Below are the previews of the primary tabs in KeepInTouch.

### 📞 Tab 1: Daily Agenda
*The Daily Agenda is your friendly dashboard. It summarizes who is due for a catch-up today and lets you snooze or manually log communications in a single tap.*

> **[Insert Daily Agenda Screen Screenshot Here]**
> *Placeholder link: https://github.com/your-username/your-repo/raw/main/screenshots/tab_agenda.png*

---

### 📇 Tab 2: Contacts List
*Browse all your imported phone contacts, check their group assignments, search for specific entries, and view detailed timelines of your conversations and notes.*

> **[Insert Contacts List Screen Screenshot Here]**
> *Placeholder link: https://github.com/your-username/your-repo/raw/main/screenshots/tab_contacts.png*

---

### 👥 Tab 3: Groups Manager
*Create custom categories like "Family", "Close Friends", or "Work". Each group displays priority and frequency pills. To safeguard your groups, deleting a group requires a 5-second countdown accompanied by a visual filling bar to prevent accidental clicks.*

> **[Insert Groups Manager Screen Screenshot Here]**
> *Placeholder link: https://github.com/your-username/your-repo/raw/main/screenshots/tab_groups.png*

---

### ⚙️ Tab 4: Settings Screen
*Configure application defaults, check system permissions, and perform standard JSON backups to safely export or restore your relationship database.*

> **[Insert Settings Screen Screenshot Here]**
> *Placeholder link: https://github.com/your-username/your-repo/raw/main/screenshots/tab_settings.png*

---

## Core Features (How They Work)

### 1. Smart Call Sensing
KeepInTouch uses Android system permissions (`READ_CALL_LOG` & `READ_CONTACTS`) to automatically detect when you complete a standard phone call. As soon as you finish a call with an assigned contact, KeepInTouch updates their history and moves them to the bottom of your agenda list automatically.

### 2. Group Cadence & Priority Pills
* **Reminder Frequency:** Specify how often you want to check in on a group (e.g., every 30 days). KeepInTouch displays these values as neat horizontal **Frequency Pills** (like `30d`).
* **Priority Level:** Distinguish urgent connections with **Priority Pills** (*Low*, *Normal*, *High*). High-priority groups display a signature gold star indicator.

### 3. Custom Reminders & Overrides
You don't have to stick strictly to group settings! In the contact configuration dialog, you can:
* Use a horizontal slider selector to override a contact's priority instantly in one line.
* Set custom contact frequencies using simple `+` and `-` button steppers.

### 4. Delayed Safety Shield (Group Deletion)
Accidentally deleting an entire group can be stressful. To protect your data, deleting a group triggers our smart confirmation shield:
* A modal dialog will appear and lock the "Delete" button for **5 seconds**.
* A red filling bar visualizes the countdown progress from left to right.
* Once the countdown completes and the bar is fully filled, the "Delete Group" button activates, ensuring you are making a conscious decision.

---

## Step-by-Step Guide: How to Use KeepInTouch

### Step 1: Grant Permissions on First Run
When you launch the app, grant the requested **Call Log** and **Contacts** permissions. This allows KeepInTouch to automatically sync your calls and import contact names.

### Step 2: Set Up Your Groups
1. Go to the **Groups** tab.
2. Tap the Floating Action Button (+) or edit existing groups (like *Close Friends*, *Family*, or *Work*).
3. Set your preferred default frequency (in days) and choose a priority level.

### Step 3: Assign Groups to Your Contacts
1. Open the **Contacts** tab.
2. Search for a friend or family member and tap on their card.
3. Tap **Configure Reminders**.
4. Choose their associated group, or apply a specific custom frequency/priority override.

### Step 4: Track Your Agenda Daily
1. Check your **Agenda** tab. Anyone who is due for a call will be displayed at the top of the list.
2. Tap the phone dialer icon to call them directly.
3. If you spoke to them through another medium (like WhatsApp), tap the instant log button.
4. If you are busy, click snooze to postpone the reminder.

### Step 5: Keep Backups
To migrate your data to a new phone:
1. Navigate to the **Settings** tab.
2. Under "Backup & Restore", click **Export Backup**. This saves a lightweight, secure JSON file containing your groups and reminder settings.
3. On your new phone, copy the file over and click **Restore Backup**.

---

## Privacy Promise
KeepInTouch runs **100% offline**. It has no tracker SDKs, does not send your call log details to external cloud services, and respects your absolute privacy. Your relationships are yours alone to manage.
