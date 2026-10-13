package com.stats.app.data.util

import com.stats.app.data.model.StatRangeType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * 统计区间与逻辑日计算。
 *
 * 口径（对齐方案文档）：
 *  - 日：逻辑日（受日界线 dayStartHour 影响：凌晨该点前算前一天）
 *  - 周：ISO 周，周一开始
 *  - 月 / 年：自然月 / 自然年
 * 所有区间一律为 [start, endExclusive)。
 */
object DateRanges {

    private val zone: ZoneId = ZoneId.systemDefault()

    data class DayRange(val start: LocalDate, val endExclusive: LocalDate) {
        fun contains(d: LocalDate): Boolean = !d.isBefore(start) && d.isBefore(endExclusive)

        /** 枚举区间内全部日期（用于趋势分桶聚合） */
        fun days(): List<LocalDate> =
            generateSequence(start) { it.plusDays(1) }
                .takeWhile { it.isBefore(endExclusive) }
                .toList()
    }

    /**
     * 逻辑日：日界线之前的时刻属于前一天。
     * 例：dayStartHour = 4 时，凌晨 2:00 的"今天"是昨天。
     */
    fun logicalToday(dayStartHour: Int, now: LocalDateTime = LocalDateTime.now()): LocalDate =
        if (now.hour < dayStartHour) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** 当前周期（完整周期，如本周 = 周一到下周一） */
    fun current(type: StatRangeType, today: LocalDate): DayRange = when (type) {
        StatRangeType.DAY -> DayRange(today, today.plusDays(1))
        StatRangeType.WEEK -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            DayRange(monday, monday.plusWeeks(1))
        }
        StatRangeType.MONTH -> {
            val first = today.withDayOfMonth(1)
            DayRange(first, first.plusMonths(1))
        }
        StatRangeType.YEAR -> {
            val first = today.withDayOfYear(1)
            DayRange(first, first.plusYears(1))
        }
    }

    /** 上一周期（用于环比） */
    fun previous(type: StatRangeType, today: LocalDate): DayRange {
        val cur = current(type, today)
        return when (type) {
            StatRangeType.DAY -> DayRange(cur.start.minusDays(1), cur.start)
            StatRangeType.WEEK -> DayRange(cur.start.minusWeeks(1), cur.start)
            StatRangeType.MONTH -> DayRange(cur.start.minusMonths(1), cur.start)
            StatRangeType.YEAR -> DayRange(cur.start.minusYears(1), cur.start)
        }
    }

    /** 趋势桶：从旧到新的 count 个周期区间（含当前周期） */
    fun trendBuckets(type: StatRangeType, today: LocalDate, count: Int = 7): List<DayRange> =
        (count - 1 downTo 0).map { back -> current(type, shift(type, today, -back)) }

    /** 按类型平移日期 n 个周期（n 可正可负），用于统计周期翻页 */
    fun shift(type: StatRangeType, date: LocalDate, n: Int): LocalDate = when (type) {
        StatRangeType.DAY -> date.plusDays(n.toLong())
        StatRangeType.WEEK -> date.plusWeeks(n.toLong())
        StatRangeType.MONTH -> date.plusMonths(n.toLong())
        StatRangeType.YEAR -> date.plusYears(n.toLong())
    }

    /** 趋势桶的显示标签，如 10/10、W41、10月、2026 */
    fun bucketLabel(type: StatRangeType, range: DayRange): String = when (type) {
        StatRangeType.DAY -> "%d/%d".format(range.start.monthValue, range.start.dayOfMonth)
        StatRangeType.WEEK -> {
            val wf = WeekFields.ISO
            "W%02d".format(range.start.get(wf.weekOfWeekBasedYear()))
        }
        StatRangeType.MONTH -> "%d月".format(range.start.monthValue)
        StatRangeType.YEAR -> "%d".format(range.start.year)
    }

    /**
     * 逻辑日的毫秒起点：逻辑日 d 的时刻区间起点 = d 的 dayStartHour 点。
     * （dayStartHour = 0 时即自然日 00:00）
     */
    fun startMillis(d: LocalDate, dayStartHour: Int = 0): Long =
        d.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()

    /** 区间 → 毫秒查询边界 [from, to) */
    fun toMillis(range: DayRange, dayStartHour: Int = 0): Pair<Long, Long> =
        startMillis(range.start, dayStartHour) to startMillis(range.endExclusive, dayStartHour)
}
