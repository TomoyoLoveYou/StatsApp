package com.stats.app.ui.common

import android.content.Context
import com.stats.app.R
import com.stats.app.data.model.AppLanguage
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.StatRangeType
import com.stats.app.data.model.ThemeMode
import com.stats.app.data.model.TriggerAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * 全部文案函数接受 Context（而非 @Composable）：既能在任何 Composable 中调用，
 * 也能在 map / forEach 等普通 lambda 中构造选项列表。
 * Context 应取自 LocalContext.current（已按应用语言包装）。
 */

/** 按当前界面语言取 Locale（用于本地化日期格式） */
private fun localeOf(context: Context): Locale =
    context.resources.configuration.locales.get(0)

/** 日期 pattern：中文用"年月日"字面量，英文用月份缩写 */
private fun datePattern(loc: Locale, withYear: Boolean): String =
    if (loc.language == "zh") {
        if (withYear) "yyyy年M月d日" else "M月d日"
    } else {
        if (withYear) "MMM d, yyyy" else "MMM d"
    }

private fun monthPattern(loc: Locale, withYear: Boolean): String =
    if (loc.language == "zh") {
        if (withYear) "yyyy年M月" else "M月"
    } else {
        if (withYear) "MMM yyyy" else "MMM"
    }

private fun yearPattern(loc: Locale): String =
    if (loc.language == "zh") "yyyy年" else "yyyy"

private fun formatDate(context: Context, date: LocalDate, withYear: Boolean): String {
    val loc = localeOf(context)
    return date.format(DateTimeFormatter.ofPattern(datePattern(loc, withYear), loc))
}

private fun formatMonth(context: Context, date: LocalDate, withYear: Boolean): String {
    val loc = localeOf(context)
    return date.format(DateTimeFormatter.ofPattern(monthPattern(loc, withYear), loc))
}

private fun formatYear(context: Context, date: LocalDate): String {
    val loc = localeOf(context)
    return date.format(DateTimeFormatter.ofPattern(yearPattern(loc), loc))
}

private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

// ===== 任务周期类型 =====

/** 任务类型名（选择器用）：每日 / 每周 / ... */
fun PeriodType.label(context: Context): String = context.getString(
    when (this) {
        PeriodType.DAILY -> R.string.fmt_period_daily
        PeriodType.WEEKLY -> R.string.fmt_period_weekly
        PeriodType.MONTHLY -> R.string.fmt_period_monthly
        PeriodType.YEARLY -> R.string.fmt_period_yearly
        PeriodType.ONE_TIME -> R.string.fmt_period_once
        PeriodType.STANDING -> R.string.fmt_period_standing
    }
)

/** 任务卡片副标题：每天 / 每周 / ... */
fun PeriodType.everyLabel(context: Context): String = context.getString(
    when (this) {
        PeriodType.DAILY -> R.string.fmt_every_daily
        PeriodType.WEEKLY -> R.string.fmt_every_weekly
        PeriodType.MONTHLY -> R.string.fmt_every_monthly
        PeriodType.YEARLY -> R.string.fmt_every_yearly
        PeriodType.ONE_TIME -> R.string.fmt_period_once
        PeriodType.STANDING -> R.string.fmt_period_standing
    }
)

/** 当前所处周期的称呼，如"本周"（用于 "本周 1/3 次"） */
fun PeriodType.currentPeriodLabel(context: Context): String = context.getString(
    when (this) {
        PeriodType.DAILY -> R.string.fmt_period_today
        PeriodType.WEEKLY -> R.string.fmt_period_this_week
        PeriodType.MONTHLY -> R.string.fmt_period_this_month
        PeriodType.YEARLY -> R.string.fmt_period_this_year
        PeriodType.ONE_TIME -> R.string.fmt_period_once
        PeriodType.STANDING -> R.string.fmt_period_today
    }
)

// ===== 提醒级别 / 到点行为 =====

fun RemindLevel.label(context: Context): String = context.getString(
    when (this) {
        RemindLevel.SILENT -> R.string.fmt_level_silent
        RemindLevel.NOTIFY -> R.string.fmt_level_notify
        RemindLevel.BANNER -> R.string.fmt_level_banner
        RemindLevel.ALARM -> R.string.fmt_level_alarm
    }
)

fun TriggerAction.label(context: Context): String = context.getString(
    when (this) {
        TriggerAction.REMIND -> R.string.fmt_action_remind
        TriggerAction.AUTO_COMPLETE -> R.string.fmt_action_auto_complete
    }
)

// ===== 统计范围 =====

/** 统计范围标签（选择器用）：日 / 周 / 月 / 年 */
fun StatRangeType.label(context: Context): String = context.getString(
    when (this) {
        StatRangeType.DAY -> R.string.fmt_range_day
        StatRangeType.WEEK -> R.string.fmt_range_week
        StatRangeType.MONTH -> R.string.fmt_range_month
        StatRangeType.YEAR -> R.string.fmt_range_year
    }
)

/** 概览卡标题（isCurrent=false 表示正在回看历史周期） */
fun StatRangeType.scoreTitle(context: Context, isCurrent: Boolean): String = context.getString(
    if (isCurrent) {
        when (this) {
            StatRangeType.DAY -> R.string.fmt_score_today
            StatRangeType.WEEK -> R.string.fmt_score_this_week
            StatRangeType.MONTH -> R.string.fmt_score_this_month
            StatRangeType.YEAR -> R.string.fmt_score_this_year
        }
    } else {
        when (this) {
            StatRangeType.DAY -> R.string.fmt_score_that_day
            StatRangeType.WEEK -> R.string.fmt_score_that_week
            StatRangeType.MONTH -> R.string.fmt_score_that_month
            StatRangeType.YEAR -> R.string.fmt_score_that_year
        }
    }
)

/** 趋势卡副标题（回看历史时显示截至日期） */
fun StatRangeType.trendSubtitle(
    context: Context,
    isCurrent: Boolean,
    periodEnd: LocalDate,
): String {
    if (isCurrent) {
        return context.getString(
            when (this) {
                StatRangeType.DAY -> R.string.fmt_trend_last_days
                StatRangeType.WEEK -> R.string.fmt_trend_last_weeks
                StatRangeType.MONTH -> R.string.fmt_trend_last_months
                StatRangeType.YEAR -> R.string.fmt_trend_last_years
            }
        )
    }
    val until = when (this) {
        StatRangeType.DAY, StatRangeType.WEEK -> formatDate(context, periodEnd, withYear = false)
        StatRangeType.MONTH -> formatMonth(context, periodEnd, withYear = true)
        StatRangeType.YEAR -> formatYear(context, periodEnd)
    }
    return context.getString(R.string.fmt_until, until)
}

/**
 * 统计周期导航条标签，如 10月10日（今天）/ 10/5–10/11（本周）/ 10月（本月）。
 * 月视图仅在跨年或贴近跨年边界（1、12 月）时标注年份。
 */
fun periodNavigatorLabel(
    context: Context,
    type: StatRangeType,
    anchor: LocalDate,
    today: LocalDate,
    isCurrent: Boolean,
): String {
    val suffixRes: Int? = when {
        type == StatRangeType.DAY && anchor == today -> R.string.fmt_suffix_today
        type == StatRangeType.DAY && anchor == today.minusDays(1) -> R.string.fmt_suffix_yesterday
        type == StatRangeType.WEEK && isCurrent -> R.string.fmt_suffix_this_week
        type == StatRangeType.MONTH && isCurrent -> R.string.fmt_suffix_this_month
        type == StatRangeType.YEAR && isCurrent -> R.string.fmt_suffix_this_year
        else -> null
    }
    val suffix = suffixRes?.let { context.getString(it) } ?: ""
    val main = when (type) {
        StatRangeType.DAY -> formatDate(context, anchor, withYear = anchor.year != today.year)
        StatRangeType.WEEK -> {
            val monday = mondayOf(anchor)
            val sunday = monday.plusDays(6)
            val range = "%d/%d – %d/%d".format(
                monday.monthValue, monday.dayOfMonth,
                sunday.monthValue, sunday.dayOfMonth,
            )
            if (monday.year != today.year) {
                context.getString(R.string.fmt_week_with_year, monday.year, range)
            } else {
                range
            }
        }
        StatRangeType.MONTH -> {
            // 同年月份只显示"10月"；跨年、或贴近年份边界的 1/12 月才标年份
            val needYear = anchor.year != today.year ||
                    anchor.monthValue == 1 || anchor.monthValue == 12
            formatMonth(context, anchor, withYear = needYear)
        }
        StatRangeType.YEAR -> formatYear(context, anchor)
    }
    return main + suffix
}

/** M月d日 简写 */
fun dateShortLabel(context: Context, date: LocalDate): String =
    formatDate(context, date, withYear = false)

/** 月份简写（趋势图轴标签用）：中文"10月" / 英文"Oct" */
fun monthShortLabel(context: Context, month: Int): String =
    formatMonth(context, LocalDate.of(2000, month, 1), withYear = false)

/** 毫秒时间戳 → "M月d日 HH:mm"（跨年时带年份），用于历史任务页 */
fun formatDateTime(context: Context, millis: Long): String {
    val loc = localeOf(context)
    val dt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val withYear = dt.year != LocalDate.now().year
    val pattern = if (loc.language == "zh") {
        if (withYear) "yyyy年M月d日 HH:mm" else "M月d日 HH:mm"
    } else {
        if (withYear) "MMM d, yyyy HH:mm" else "MMM d HH:mm"
    }
    return dt.format(DateTimeFormatter.ofPattern(pattern, loc))
}

/** 补卡日期标签：昨天 / 前天 / MM-dd */
fun backfillDayLabel(context: Context, date: LocalDate): String {
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when (daysAgo) {
        1L -> context.getString(R.string.fmt_yesterday)
        2L -> context.getString(R.string.fmt_two_days_ago)
        else -> "%d-%02d".format(date.monthValue, date.dayOfMonth)
    }
}

/** 日期 → DatePicker 存储用 UTC 毫秒 */
fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** DatePicker 返回的 UTC 毫秒 → 日期 */
fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

// ===== 设置项 =====

/** 主题模式标签 */
fun ThemeMode.label(context: Context): String = context.getString(
    when (this) {
        ThemeMode.SYSTEM -> R.string.fmt_theme_system
        ThemeMode.LIGHT -> R.string.fmt_theme_light
        ThemeMode.DARK -> R.string.fmt_theme_dark
    }
)

/** 界面语言标签 */
fun AppLanguage.label(context: Context): String = context.getString(
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.ZH -> R.string.settings_language_zh
        AppLanguage.EN -> R.string.settings_language_en
    }
)

/** 日界线标签 */
fun dayStartHourLabel(context: Context, hour: Int): String =
    if (hour == 0) {
        context.getString(R.string.fmt_day_start_midnight)
    } else {
        context.getString(R.string.fmt_day_start_hour, hour)
    }
