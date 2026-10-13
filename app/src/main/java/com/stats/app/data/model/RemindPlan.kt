package com.stats.app.data.model

import kotlinx.serialization.Serializable

/**
 * 提醒计划。
 * M1 仅使用 time；weekDays / monthDay / yearMonth / yearDay 为 M3 提醒系统预留。
 */
@Serializable
data class RemindPlan(
    val time: String = "08:00",
    val weekDays: List<Int> = emptyList(),
    val monthDay: Int? = null,
    val yearMonth: Int? = null,
    val yearDay: Int? = null,
)
