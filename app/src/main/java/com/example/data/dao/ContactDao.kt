package com.example.data.dao

import androidx.room.*
import com.example.data.dto.ContactWithDetails
import com.example.data.model.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): ContactEntity?

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :id")
    fun getContactWithDetailsFlow(id: Long): Flow<ContactWithDetails?>

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContactsWithDetailsFlow(): Flow<List<ContactWithDetails>>

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContactsWithDetails(): List<ContactWithDetails>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhoneNumber(phoneNumber: String): ContactEntity?

    @Query("UPDATE interaction_logs SET contactId = :targetContactId WHERE contactId = :sourceContactId")
    suspend fun transferInteractionLogs(sourceContactId: Long, targetContactId: Long)

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: Long)
}
