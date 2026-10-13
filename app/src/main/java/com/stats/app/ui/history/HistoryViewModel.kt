package com.stats.app.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stats.app.R
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.PeriodType
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.CheckInRepository
import com.stats.app.data.repository.TaskRepository
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 已完成的单次任务（从任务列表消失，可撤销完成） */
data class CompletedOneTimeItem(
    val task: TaskEntity,
    val completedAt: Long?,
)

/** 已删除（归档）的任务（可恢复） */
data class ArchivedTaskItem(
    val task: TaskEntity,
    val archivedAt: Long,
    val checkInCount: Int,
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val completedOneTime: List<CompletedOneTimeItem> = emptyList(),
    val archived: List<ArchivedTaskItem> = emptyList(),
    val categories: Map<Long, PointsCategoryEntity> = emptyMap(),
)

/**
 * 历史任务：
 *  - 已完成的单次任务（完成后自动从任务列表消失）；
 *  - 已删除的任务（软删除，可恢复）。
 * 两类的历史打卡与积分始终保留在统计中。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    private val checkInRepository: CheckInRepository,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        taskRepository.observeAll(),
        taskRepository.observeArchived(),
        categoryRepository.observeAll(),
        checkInRepository.observeActiveCounts(),
        checkInRepository.observeLastActiveTimes(),
    ) { tasks, archived, categories, countRows, lastRows ->
        val countByTask = countRows.groupBy { it.taskId }
        fun totalCount(taskId: Long): Int = countByTask[taskId]?.sumOf { it.count } ?: 0
        val lastByTask = lastRows.associate { it.taskId to it.lastCompletedAt }

        val completed = tasks
            .filter { it.periodType == PeriodType.ONE_TIME && totalCount(it.id) >= 1 }
            .sortedByDescending { lastByTask[it.id] ?: 0L }
            .map { CompletedOneTimeItem(it, lastByTask[it.id]) }

        val archivedItems = archived.map {
            ArchivedTaskItem(
                task = it,
                archivedAt = it.archivedAt ?: 0L,
                checkInCount = totalCount(it.id),
            )
        }

        HistoryUiState(
            loaded = true,
            completedOneTime = completed,
            archived = archivedItems,
            categories = categories.associateBy { it.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    /** 操作反馈（一次性消费的 Channel 事件流，离开页面再回来不会重复弹出） */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 撤销单次任务的完成：回到任务列表（积分同步扣回） */
    fun undoCompleted(task: TaskEntity) {
        viewModelScope.launch {
            checkInRepository.undoLatestAny(task)
            _messages.trySend(AppLocale.string(context, R.string.history_msg_undo, task.name))
            AppLog.i("History", "撤销单次任务完成 #${task.id}「${task.name}」")
        }
    }

    /** 恢复已删除的任务：回到任务列表并恢复提醒 */
    fun restoreArchived(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.restore(task)
            _messages.trySend(AppLocale.string(context, R.string.history_msg_restore, task.name))
        }
    }

    init {
        AppLog.i("History", "HistoryViewModel 初始化")
    }
}
