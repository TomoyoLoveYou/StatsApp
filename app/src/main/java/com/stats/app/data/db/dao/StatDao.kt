package com.stats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 区间内按分类聚合的积分 */
data class CategorySumRow(val categoryId: Long, val total: Long)

/** 按天聚合的积分（day 为逻辑日，形如 2026-10-10） */
data class DaySumRow(val day: String, val total: Long)

/** 任务排行行（打卡次数 + 任务所属分类，用于同名任务的区分显示） */
data class TaskRankRow(
    val taskId: Long,
    val taskName: String,
    val categoryId: Long,
    val count: Int,
)

/** 任务积分行（区间内该任务关联流水净额） */
data class TaskPointsRow(val taskId: Long, val total: Long)

/** 按天 × 分类聚合的积分（热力图取"当日积分最高的分类"） */
data class DayCategorySumRow(val day: String, val categoryId: Long, val total: Long)

/**
 * 统计聚合查询。
 * 全部为 GROUP BY 聚合，自用数据量下 SQLite 即时完成，不需要预计算表。
 *
 * 日界线：按天分桶的查询把时间戳回拨 dayStartHour 小时再取日期
 * （凌晨该点前的记录归入前一天），与 [com.stats.app.data.util.DateRanges.logicalToday] 口径一致。
 */
@Dao
interface StatDao {

    /** 区间内积分流水净额（含撤销负分） */
    @Query("SELECT COALESCE(SUM(value), 0) FROM points_record WHERE createdAt >= :from AND createdAt < :to")
    fun observePointsSum(from: Long, to: Long): Flow<Long>

    /** 区间内按分类的积分构成 */
    @Query(
        """
        SELECT categoryId AS categoryId, COALESCE(SUM(value), 0) AS total
        FROM points_record
        WHERE createdAt >= :from AND createdAt < :to
        GROUP BY categoryId
        """
    )
    fun observePointsByCategory(from: Long, to: Long): Flow<List<CategorySumRow>>

    /** 区间内按天（逻辑日）的积分（用于趋势图分桶） */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', createdAt / 1000 - :dayStartHour * 3600, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(value), 0) AS total
        FROM points_record
        WHERE createdAt >= :from AND createdAt < :to
        GROUP BY day
        """
    )
    fun observePointsByDay(from: Long, to: Long, dayStartHour: Int): Flow<List<DaySumRow>>

    /** 区间内有效打卡次数 */
    @Query("SELECT COUNT(*) FROM check_in WHERE revoked = 0 AND completedAt >= :from AND completedAt < :to")
    fun observeCheckInCount(from: Long, to: Long): Flow<Int>

    /** 区间内任务排行（按有效打卡次数；已删除任务保留历史并以「已删除任务」展示） */
    @Query(
        """
        SELECT c.taskId AS taskId,
               COALESCE(t.name, '已删除任务') AS taskName,
               COALESCE(t.categoryId, 0) AS categoryId,
               COUNT(*) AS count
        FROM check_in c
        LEFT JOIN task t ON t.id = c.taskId
        WHERE c.revoked = 0 AND c.completedAt >= :from AND c.completedAt < :to
        GROUP BY c.taskId
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun observeTaskRank(from: Long, to: Long, limit: Int): Flow<List<TaskRankRow>>

    /** 区间内各任务关联流水的净额（含撤销负分，与总积分口径一致） */
    @Query(
        """
        SELECT c.taskId AS taskId, COALESCE(SUM(p.value), 0) AS total
        FROM points_record p
        JOIN check_in c ON c.id = p.checkInId
        WHERE p.createdAt >= :from AND p.createdAt < :to
        GROUP BY c.taskId
        """
    )
    fun observeTaskPoints(from: Long, to: Long): Flow<List<TaskPointsRow>>

    /** 区间内按天 × 分类的积分（热力图：每天取积分最高的分类着色） */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', createdAt / 1000 - :dayStartHour * 3600, 'unixepoch', 'localtime') AS day,
               categoryId AS categoryId,
               COALESCE(SUM(value), 0) AS total
        FROM points_record
        WHERE createdAt >= :from AND createdAt < :to
        GROUP BY day, categoryId
        """
    )
    fun observePointsByDayAndCategory(from: Long, to: Long, dayStartHour: Int): Flow<List<DayCategorySumRow>>

    /** 全部有效打卡日期（逻辑日，降序，用于连续打卡计算） */
    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m-%d', completedAt / 1000 - :dayStartHour * 3600, 'unixepoch', 'localtime') AS day
        FROM check_in
        WHERE revoked = 0
        ORDER BY day DESC
        LIMIT 730
        """
    )
    fun observeActiveDays(dayStartHour: Int): Flow<List<String>>
}
