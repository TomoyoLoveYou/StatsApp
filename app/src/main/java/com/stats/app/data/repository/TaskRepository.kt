package com.stats.app.data.repository

import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.reminder.ReminderScheduler
import com.stats.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val scheduler: ReminderScheduler,
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    /** 已删除（归档）的任务列表（历史任务页） */
    fun observeArchived(): Flow<List<TaskEntity>> = taskDao.observeArchived()

    suspend fun get(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun create(task: TaskEntity): Long {
        val id = taskDao.insert(task)
        scheduler.schedule(task.copy(id = id))
        AppLog.i(
            "TaskRepo",
            "新建任务 #$id「${task.name}」周期=${task.periodType} 行为=${task.triggerAction} 分类=${task.categoryId} 分值=${task.points}"
        )
        return id
    }

    suspend fun update(task: TaskEntity) {
        taskDao.update(task)
        scheduler.schedule(task)
        AppLog.i("TaskRepo", "更新任务 #${task.id}「${task.name}」启用=${task.enabled} 分值=${task.points}")
    }

    /**
     * 删除任务（软删除）：从任务列表移除并取消提醒。
     * 任务本体保留在「设置 → 历史任务」中可随时找回；
     * **历史打卡与积分记录保留**（统计、热力图、连续打卡不丢失）。
     */
    suspend fun archive(task: TaskEntity) {
        taskDao.setArchivedAt(task.id, System.currentTimeMillis())
        scheduler.cancel(task.id)
        AppLog.i("TaskRepo", "删除任务 #${task.id}「${task.name}」（可在 设置→历史任务 中找回；打卡与积分保留）")
    }

    /** 从历史任务恢复：重新出现在任务列表并恢复提醒 */
    suspend fun restore(task: TaskEntity) {
        taskDao.setArchivedAt(task.id, null)
        taskDao.getById(task.id)?.let { scheduler.schedule(it) }
        AppLog.i("TaskRepo", "恢复任务 #${task.id}「${task.name}」（已回到任务列表）")
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        taskDao.setEnabled(id, enabled)
        if (enabled) {
            taskDao.getById(id)?.let { scheduler.schedule(it) }
        } else {
            scheduler.cancel(id)
        }
        AppLog.i("TaskRepo", "任务 #$id 启用状态 → $enabled")
    }
}
