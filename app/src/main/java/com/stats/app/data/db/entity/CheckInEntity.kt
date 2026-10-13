package com.stats.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stats.app.data.model.CheckInSource
import kotlinx.serialization.Serializable

@Entity(tableName = "check_in")
@Serializable
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    /** 周期归属键，如 2026-10-10 / 2026-W41 / 2026-10 / 2026 */
    val periodKey: String,
    val completedAt: Long,
    val source: CheckInSource = CheckInSource.MANUAL,
    val revoked: Boolean = false,
)
