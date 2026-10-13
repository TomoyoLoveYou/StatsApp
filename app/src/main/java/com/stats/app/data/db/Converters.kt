package com.stats.app.data.db

import androidx.room.TypeConverter
import com.stats.app.data.model.CheckInSource
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.RemindPlan
import com.stats.app.data.model.TriggerAction
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun fromPeriodType(value: PeriodType): String = value.name

    @TypeConverter
    fun toPeriodType(value: String): PeriodType = enumValueOf(value)

    @TypeConverter
    fun fromTriggerAction(value: TriggerAction): String = value.name

    @TypeConverter
    fun toTriggerAction(value: String): TriggerAction = enumValueOf(value)

    @TypeConverter
    fun fromRemindLevel(value: RemindLevel): String = value.name

    @TypeConverter
    fun toRemindLevel(value: String): RemindLevel = enumValueOf(value)

    @TypeConverter
    fun fromCheckInSource(value: CheckInSource): String = value.name

    @TypeConverter
    fun toCheckInSource(value: String): CheckInSource = enumValueOf(value)

    @TypeConverter
    fun fromRemindPlan(value: RemindPlan): String =
        json.encodeToString(RemindPlan.serializer(), value)

    @TypeConverter
    fun toRemindPlan(value: String): RemindPlan =
        runCatching { json.decodeFromString(RemindPlan.serializer(), value) }
            .getOrDefault(RemindPlan())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
