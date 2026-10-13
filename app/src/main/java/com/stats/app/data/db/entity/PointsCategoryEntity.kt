package com.stats.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "points_category")
@Serializable
data class PointsCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** ARGB 颜色值，如 0xFF4A6CF7 */
    val color: Long,
    val sortOrder: Int = 0,
)
