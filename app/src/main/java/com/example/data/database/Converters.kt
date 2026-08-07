package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.model.InteractionType

class Converters {
    @TypeConverter
    fun fromInteractionType(type: InteractionType): String = type.name

    @TypeConverter
    fun toInteractionType(value: String): InteractionType {
        return try {
            enumValueOf(value)
        } catch (e: Exception) {
            InteractionType.MANUAL_LOG
        }
    }
}
