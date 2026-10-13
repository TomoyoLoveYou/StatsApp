package com.stats.app.data.model

/** 任务周期类型（ONE_TIME = 单次：完成一次即结束；STANDING = 常驻：不提醒、可多次完成） */
enum class PeriodType { DAILY, WEEKLY, MONTHLY, YEARLY, ONE_TIME, STANDING }

/** 到点行为：提醒完成 / 到点自动完成 */
enum class TriggerAction { REMIND, AUTO_COMPLETE }

/** 提醒强度级别 */
enum class RemindLevel { SILENT, NOTIFY, BANNER, ALARM }

/** 打卡来源 */
enum class CheckInSource { MANUAL, AUTO }

/** 统计范围（日 / 周 / 月 / 年） */
enum class StatRangeType { DAY, WEEK, MONTH, YEAR }

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 应用界面语言（SYSTEM = 跟随本机；本机为非中英语言时按英文处理） */
enum class AppLanguage { SYSTEM, ZH, EN }
