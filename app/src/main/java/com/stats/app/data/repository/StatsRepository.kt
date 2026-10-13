package com.stats.app.data.repository

import com.stats.app.data.db.dao.CategorySumRow
import com.stats.app.data.db.dao.DayCategorySumRow
import com.stats.app.data.db.dao.DaySumRow
import com.stats.app.data.db.dao.StatDao
import com.stats.app.data.db.dao.TaskPointsRow
import com.stats.app.data.db.dao.TaskRankRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 统计聚合查询入口（薄转发；按天分桶的查询需传入日界线 dayStartHour） */
@Singleton
class StatsRepository @Inject constructor(
    private val statDao: StatDao,
) {

    fun observePointsSum(from: Long, to: Long): Flow<Long> =
        statDao.observePointsSum(from, to)

    fun observePointsByCategory(from: Long, to: Long): Flow<List<CategorySumRow>> =
        statDao.observePointsByCategory(from, to)

    fun observePointsByDay(from: Long, to: Long, dayStartHour: Int): Flow<List<DaySumRow>> =
        statDao.observePointsByDay(from, to, dayStartHour)

    fun observeCheckInCount(from: Long, to: Long): Flow<Int> =
        statDao.observeCheckInCount(from, to)

    fun observeTaskRank(from: Long, to: Long, limit: Int): Flow<List<TaskRankRow>> =
        statDao.observeTaskRank(from, to, limit)

    fun observeTaskPoints(from: Long, to: Long): Flow<List<TaskPointsRow>> =
        statDao.observeTaskPoints(from, to)

    fun observePointsByDayAndCategory(from: Long, to: Long, dayStartHour: Int): Flow<List<DayCategorySumRow>> =
        statDao.observePointsByDayAndCategory(from, to, dayStartHour)

    fun observeActiveDays(dayStartHour: Int): Flow<List<String>> =
        statDao.observeActiveDays(dayStartHour)
}
