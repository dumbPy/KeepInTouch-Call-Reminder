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

    fun isPhoneMatch(num1: String, num2: String): Boolean {
        val norm1 = num1.replace(Regex("[^0-9]"), "")
        val norm2 = num2.replace(Regex("[^0-9]"), "")
        if (norm1.isEmpty() || norm2.isEmpty()) return false
        if (norm1 == norm2) return true
        val tail1 = if (norm1.length >= 7) norm1.takeLast(7) else norm1
        val tail2 = if (norm2.length >= 7) norm2.takeLast(7) else norm2
        return tail1 == tail2
    }

    /**
     * Set a selected phone number as default (primary & super primary) in system contacts.
     */
    fun setSystemContactDefaultPhone(
        context: Context,
        systemContactId: Long?,
        lookupKey: String?,
        phoneNumber: String
    ): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        try {
            val resolver = context.contentResolver
            
            val selection = when {
                !lookupKey.isNullOrBlank() -> "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?"
                systemContactId != null && systemContactId > 0 -> "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
                else -> return false
            }
            val selectionArgs = when {
                !lookupKey.isNullOrBlank() -> arrayOf(lookupKey)
                systemContactId != null && systemContactId > 0 -> arrayOf(systemContactId.toString())
                else -> return false
            }

            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                selection,
                selectionArgs,
                null
            )

            var targetDataId: Long? = null
            val allDataIds = mutableListOf<Long>()

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.Data._ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val dataId = c.getLong(idIdx)
                    val num = c.getString(numIdx) ?: continue
                    allDataIds.add(dataId)
                    if (isPhoneMatch(num, phoneNumber)) {
                        targetDataId = dataId
                    }
                }
            }

            if (targetDataId != null) {
                val ops = arrayListOf<android.content.ContentProviderOperation>()
                
                // Set the target number to primary and super primary
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Data.IS_PRIMARY, 1)
                    put(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                }
                ops.add(
                    android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(targetDataId.toString()))
                        .withValues(values)
                        .build()
                )

                // Reset IS_SUPER_PRIMARY on other phone numbers for the same contact to avoid conflicts
                for (otherId in allDataIds) {
                    if (otherId != targetDataId) {
                        val resetValues = android.content.ContentValues().apply {
                            put(ContactsContract.Data.IS_PRIMARY, 0)
                            put(ContactsContract.Data.IS_SUPER_PRIMARY, 0)
                        }
                        ops.add(
                            android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                                .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(otherId.toString()))
                                .withValues(resetValues)
                                .build()
                        )
                    }
                }

                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
