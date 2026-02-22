package com.flow.data.local

import androidx.room.TypeConverter

/**
 * Room TypeConverters for [AchievementType] enum.
 * Stores the enum as its name string in SQLite.
 */
class AchievementTypeConverter {
    @TypeConverter
    fun fromAchievementType(type: AchievementType): String = type.name

    @TypeConverter
    fun toAchievementType(value: String): AchievementType = AchievementType.valueOf(value)
}
