package com.example.data.model

enum class InteractionType(val label: String, val isCallTouchpoint: Boolean) {
    INCOMING_CALL("Incoming Call", true),
    OUTGOING_CALL("Outgoing Call", true),
    MANUAL_LOG("Manual Call Log", true),
    WHATSAPP_CALL("WhatsApp Call", true),
    WHATSAPP_CHAT("WhatsApp Message", false),
    SNOOZE("Snoozed Reminder", false),
    NOTE("Note Added", false)
}
