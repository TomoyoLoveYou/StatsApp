package com.stats.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.RemindPlan
import com.stats.app.data.model.TriggerAction
import kotlinx.serialization.Serializable

@Entity(tableName = "task")
@Serializable
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val note: String = "",
    val periodType: PeriodType = PeriodType.DAILY,
    val periodTarget: Int = 1,
    val remindPlan: RemindPlan = RemindPlan(),
    val triggerAction: TriggerAction = TriggerAction.REMIND,
    val remindLevel: RemindLevel = RemindLevel.NOTIFY,
    val vibrate: Boolean = false,
    val categoryId: Long = 0L,
    val points: Int = 10,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null,
)
