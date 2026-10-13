package com.stats.app.data.util

import com.stats.app.data.model.PeriodType
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * 周期归属键计算。
 * 每次打卡都会写入任务所属周期的键，用于"本期是否完成 / 完成了几次"的判断与统计。
 *
 * 规则（对齐方案文档）：
 *  - 每日：2026-10-10
 *  - 每周：2026-W41（ISO 周，周一开始）
 *  - 每月：2026-10
 *  - 每年：2026
 *  - 单次 / 常驻：2026-10-10（按打卡当天记录，仅用于历史归属与补卡判重）
 */
object PeriodKeys {

    fun periodKey(type: PeriodType, date: LocalDate): String = when (type) {
        PeriodType.DAILY -> date.toString()
        PeriodType.WEEKLY -> {
            val wf = WeekFields.ISO
            "%d-W%02d".format(date.get(wf.weekBasedYear()), date.get(wf.weekOfWeekBasedYear()))
        }
        PeriodType.MONTHLY -> "%d-%02d".format(date.year, date.monthValue)
        PeriodType.YEARLY -> date.year.toString()
        PeriodType.ONE_TIME, PeriodType.STANDING -> date.toString()
    }
}
