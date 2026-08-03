package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.model.InteractionType
import com.example.data.model.TagCategory

class Converters {
    @TypeConverter
    fun fromTagCategory(category: TagCategory): String = category.name

    @TypeConverter
    fun toTagCategory(value: String): TagCategory = enumValueOf(value)

    @TypeConverter
    fun fromInteractionType(type: InteractionType): String = type.name

    @TypeConverter
    fun toInteractionType(value: String): InteractionType = enumValueOf(value)
}
