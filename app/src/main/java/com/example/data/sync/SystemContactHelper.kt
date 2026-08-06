package com.example.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.model.PhoneDetail

object SystemContactHelper {

    /**
     * Reads phone numbers with native Android labels (Mobile, Work, Home, Main, Custom, etc.)
     * and photo URIs directly from Android ContactsContract.
     */
    fun fetchPhoneDetailsAndPhoto(
        context: Context,
        systemContactId: Long?,
        lookupKey: String?,
        fallbackPhoneNumber: String,
        fallbackSecondaryNumbers: String?
    ): Pair<List<PhoneDetail>, String?> {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Pair(buildFallbackDetails(fallbackPhoneNumber, fallbackSecondaryNumbers), null)
        }

        var photoUriStr: String? = null
        val phoneList = mutableListOf<PhoneDetail>()

        try {
            // 1. Fetch photo URI if systemContactId or lookupKey is available
            val selection = when {
                !lookupKey.isNullOrBlank() -> "${ContactsContract.Contacts.LOOKUP_KEY} = ?"
                systemContactId != null && systemContactId > 0 -> "${ContactsContract.Contacts._ID} = ?"
                else -> null
            }
            val selectionArgs = when {
                !lookupKey.isNullOrBlank() -> arrayOf(lookupKey)
                systemContactId != null && systemContactId > 0 -> arrayOf(systemContactId.toString())
                else -> null
            }

            if (selection != null && selectionArgs != null) {
                val photoCursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                    ),
                    selection,
                    selectionArgs,
                    null
                )
                photoCursor?.use { c ->
                    if (c.moveToFirst()) {
                        val uriIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                        if (uriIdx != -1) photoUriStr = c.getString(uriIdx)
                        if (photoUriStr.isNullOrBlank()) {
                            val thumbIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                            if (thumbIdx != -1) photoUriStr = c.getString(thumbIdx)
                        }
                    }
                }
            }

            // 2. Query Phone data table for all numbers & labels
            val phoneSelection = when {
                !lookupKey.isNullOrBlank() -> "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?"
                systemContactId != null && systemContactId > 0 -> "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                fallbackPhoneNumber.isNotBlank() -> "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                else -> null
            }
            val phoneSelectionArgs = when {
                !lookupKey.isNullOrBlank() -> arrayOf(lookupKey)
                systemContactId != null && systemContactId > 0 -> arrayOf(systemContactId.toString())
                fallbackPhoneNumber.isNotBlank() -> arrayOf("%${fallbackPhoneNumber.takeLast(7)}%")
                else -> null
            }

            if (phoneSelection != null && phoneSelectionArgs != null) {
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    ),
                    phoneSelection,
                    phoneSelectionArgs,
                    null
                )

                phoneCursor?.use { c ->
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                    val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                    while (c.moveToNext()) {
                        val rawNum = c.getString(numIdx) ?: continue
                        if (rawNum.isBlank()) continue
                        val type = c.getInt(typeIdx)
                        val customLabel = c.getString(labelIdx)

                        if (photoUriStr.isNullOrBlank() && photoIdx != -1) {
                            photoUriStr = c.getString(photoIdx)
                        }

                        // Get native Android label (e.g. Mobile, Work, Home, Main, or custom label string)
                        val labelSeq = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            type,
                            customLabel
                        )
                        val label = labelSeq?.toString()?.ifBlank { "Phone" } ?: "Phone"

                        val trimmedNum = rawNum.trim()
                        if (!phoneList.any { it.number == trimmedNum }) {
                            phoneList.add(PhoneDetail(trimmedNum, label))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (phoneList.isEmpty()) {
            return Pair(buildFallbackDetails(fallbackPhoneNumber, fallbackSecondaryNumbers), photoUriStr)
        }

        return Pair(phoneList, photoUriStr)
    }

    private fun buildFallbackDetails(primary: String, secondary: String?): List<PhoneDetail> {
        val list = mutableListOf<PhoneDetail>()
        if (primary.isNotBlank()) {
            list.add(PhoneDetail(primary.trim(), "Mobile"))
        }
        if (!secondary.isNullOrBlank()) {
            secondary.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && !list.any { item -> item.number == it } }
                .forEachIndexed { idx, num ->
                    val label = if (idx == 0) "Work" else "Other"
                    list.add(PhoneDetail(num, label))
                }
        }
        return list
    }
}
