package com.flow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * T006 — A single earned achievement badge.
 *
 * Rows are inserted via `CONFLICT IGNORE` (idempotent) — a badge earned once
 * is permanently stored and never deleted.
 */
@Entity(
    tableName = "achievements",
    indices = [androidx.room.Index(
        value = ["type", "taskId", "periodLabel"],
        unique = true,
        name = "idx_achievements_unique"
    )]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** The type of award earned. Stored as its name string via [AchievementTypeConverter]. */
    val type: AchievementType,
    /** The recurring task involved, or null for global awards (ON_TIME_10, YEAR_FINISHER). */
    val taskId: Long? = null,
    /** Epoch-ms when the badge was earned. */
    val earnedAt: Long = System.currentTimeMillis(),
    /** E.g. "2026" for YEAR_FINISHER, or null for streak/task-level awards. */
    val periodLabel: String? = null
)
