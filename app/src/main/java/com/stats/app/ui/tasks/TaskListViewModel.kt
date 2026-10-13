package com.stats.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.PeriodType
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.CheckInRepository
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.data.repository.TaskRepository
import com.stats.app.data.util.DateRanges
import com.stats.app.data.util.PeriodKeys
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TaskListUiState(
    val loaded: Boolean = false,
    /** 列表展示的任务：已完成的单次任务不在此列（自动移至「设置 → 历史任务」） */
    val tasks: List<TaskEntity> = emptyList(),
    val categories: Map<Long, PointsCategoryEntity> = emptyMap(),
    /**
     * 进度/计数：
     *  - 周期任务 = 本周期已完成次数；
     *  - 单次任务 = 累计有效打卡数（>= 1 即已完成）；
     *  - 常驻任务 = 今天已记录的次数。
     */
    val counts: Map<Long, Int> = emptyMap(),
)

data class BackfillOption(val date: LocalDate, val alreadyDone: Boolean)

data class BackfillUiState(
    val task: TaskEntity,
    val options: List<BackfillOption>,
)

/** 单次任务刚完成的事件（用于弹出可撤销的提示条） */
data class OneTimeDoneEvent(val taskId: Long, val taskName: String)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    categoryRepository: CategoryRepository,
    private val checkInRepository: CheckInRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TaskListUiState> = combine(
        taskRepository.observeAll(),
        categoryRepository.observeAll(),
        checkInRepository.observeActiveCounts(),
        settingsRepository.dayStartHourFlow,
    ) { tasks, categories, countRows, dayStartHour ->
        val today = DateRanges.logicalToday(dayStartHour)
        val counts = tasks.associate { task ->
            val value = if (task.periodType == PeriodType.ONE_TIME) {
                // 单次任务：累计（跨周期）有效打卡数
                countRows.filter { it.taskId == task.id }.sumOf { it.count }
            } else {
                // 周期 / 常驻任务：当前周期键的次数（常驻的周期键 = 当天）
                val key = PeriodKeys.periodKey(task.periodType, today)
                countRows.firstOrNull { it.taskId == task.id && it.periodKey == key }?.count ?: 0
            }
            task.id to value
        }
        // 已完成的单次任务不再显示在任务列表（自动进入「设置 → 历史任务」）
        val visibleTasks = tasks.filter { task ->
            !(task.periodType == PeriodType.ONE_TIME && (counts[task.id] ?: 0) >= 1)
        }
        TaskListUiState(
            loaded = true,
            tasks = visibleTasks,
            categories = categories.associateBy { it.id },
            counts = counts,
        )
    }
        .onEach { AppLog.i("TaskList", "列表更新 tasks=${it.tasks.size} counts=${it.counts.values.sum()}") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskListUiState(),
        )

    private val _backfill = MutableStateFlow<BackfillUiState?>(null)
    val backfill: StateFlow<BackfillUiState?> = _backfill.asStateFlow()

    /**
     * 单次任务完成事件（一次性消费的 Channel 事件流）：
     * 展示过一次即销毁，离开页面再回来不会重复弹出。
     */
    private val _oneTimeEvents = Channel<OneTimeDoneEvent>(Channel.BUFFERED)
    val oneTimeEvents = _oneTimeEvents.receiveAsFlow()

    init {
        AppLog.i("TaskList", "TaskListViewModel 初始化")
    }

    /**
     * 点击圆环：
     *  - 常驻任务：每次 +1（可多次完成）；
     *  - 周期任务：未完成 → 打卡；已完成 → 撤销；
     *  - 单次任务：完成即从列表消失（发事件提示去哪找）。
     */
    fun toggleCheckIn(task: TaskEntity) {
        viewModelScope.launch {
            val current = uiState.value.counts[task.id] ?: 0
            when {
                task.periodType == PeriodType.STANDING -> checkInRepository.checkIn(task)

                current >= task.periodTarget -> {
                    if (task.periodType == PeriodType.ONE_TIME) {
                        checkInRepository.undoLatestAny(task)
                    } else {
                        checkInRepository.undoLatest(task)
                    }
                }

                else -> {
                    checkInRepository.checkIn(task)
                    if (task.periodType == PeriodType.ONE_TIME) {
                        _oneTimeEvents.trySend(OneTimeDoneEvent(task.id, task.name))
                    }
                }
            }
        }
    }

    /** 撤销单次任务的完成（提示条快捷操作：回到任务列表） */
    fun undoOneTimeDone(taskId: Long) {
        viewModelScope.launch {
            taskRepository.get(taskId)?.let { checkInRepository.undoLatestAny(it) }
        }
    }

    /** 撤销最近一次打卡（不限周期；常驻任务长按菜单用） */
    fun undoLastAny(task: TaskEntity) {
        viewModelScope.launch { checkInRepository.undoLatestAny(task) }
    }

    fun setEnabled(task: TaskEntity, enabled: Boolean) {
        viewModelScope.launch { taskRepository.setEnabled(task.id, enabled) }
    }

    /** 删除任务（软删除：可在「设置 → 历史任务」找回；历史打卡与积分保留在统计中） */
    fun archiveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.archive(task)
            AppLog.i("TaskList", "从列表删除任务 #${task.id}「${task.name}」（可在历史任务找回）")
        }
    }

    /** 打开补卡对话框：加载最近 7 天（逻辑日）的可补状态（该周期已达标或当天已有卡则不可补） */
    fun openBackfill(task: TaskEntity) {
        viewModelScope.launch {
            val today = checkInRepository.logicalToday()
            val options = (1..7).map { offset ->
                val date = today.minusDays(offset.toLong())
                val key = PeriodKeys.periodKey(task.periodType, date)
                val periodDone =
                    checkInRepository.getForPeriod(task.id, key).size >= task.periodTarget
                val dayDone = checkInRepository.hasCheckOnDay(task.id, date)
                BackfillOption(date, periodDone || dayDone)
            }
            _backfill.value = BackfillUiState(task, options)
            AppLog.i("TaskList", "打开补卡面板 task=#${task.id} 可补天数=${options.count { !it.alreadyDone }}")
        }
    }

    fun dismissBackfill() {
        _backfill.value = null
    }

    fun confirmBackfill(date: LocalDate) {
        val current = _backfill.value ?: return
        viewModelScope.launch {
            checkInRepository.checkIn(current.task, date)
            _backfill.value = null
        }
    }
}
