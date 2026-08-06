package com.example.data.model

/**
 * Represents a phone number with its native Android label (e.g., Mobile, Work, Home, Main, Custom).
 */
data class PhoneDetail(
    val number: String,
    val label: String
)
