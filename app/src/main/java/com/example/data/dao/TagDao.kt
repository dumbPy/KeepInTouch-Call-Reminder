package com.example.data.dao

import androidx.room.*
import com.example.data.model.TagCategory
import com.example.data.model.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Delete
    suspend fun deleteTag(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY category ASC, name ASC")
    fun getAllTagsFlow(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE category = :category ORDER BY name ASC")
    fun getTagsByCategoryFlow(category: TagCategory): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): TagEntity?

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT MIN(id) FROM tags GROUP BY name, category)")
    suspend fun deduplicateTags()
}
