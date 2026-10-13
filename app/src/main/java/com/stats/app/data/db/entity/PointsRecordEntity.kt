package com.stats.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "points_record")
@Serializable
data class PointsRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long,
    /** 关联的打卡记录（手动调整时可为空） */
    val checkInId: Long? = null,
    /** 分值，可为负（撤销打卡 / 手动扣分） */
    val value: Int,
    val createdAt: Long,
    val note: String = "",
)
