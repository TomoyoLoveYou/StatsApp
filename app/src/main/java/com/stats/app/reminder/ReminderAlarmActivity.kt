package com.stats.app.reminder

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.stats.app.R
import com.stats.app.data.model.ThemeMode
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.repository.CheckInRepository
import com.stats.app.ui.theme.StatsAppTheme
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 闹钟全屏页：播放铃声 + 循环震动，提供"完成打卡 / 稍后 10 分钟 / 关闭"。
 */
@AndroidEntryPoint
class ReminderAlarmActivity : ComponentActivity() {

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var checkInRepository: CheckInRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        AppLog.i("Alarm", "闹钟页启动 task=#$taskId 测试=$isTest")

        startAlarmEffects()

        setContent {
            StatsAppTheme(themeMode = ThemeMode.DARK) {
                var task by remember { mutableStateOf<TaskEntity?>(null) }
                LaunchedEffect(Unit) {
                    task = if (taskId > 0) taskDao.getById(taskId) else null
                }
                AlarmScreen(
                    task = task,
                    isTest = isTest,
                    onComplete = { completeTask(taskId) },
                    onSnooze = { snoozeTask(taskId) },
                    onDismiss = { stopAllAndFinish() },
                )
            }
        }
    }

    private fun startAlarmEffects() {
        // 测试/静音模式：不播放铃声与震动（避免测试时打扰）
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            AppLog.i("Alarm", "测试/静音模式：跳过铃声与震动")
            return
        }
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ReminderAlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { AppLog.e("Alarm", "铃声播放失败", it) }

        runCatching {
            vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
            )
        }.onFailure { AppLog.e("Alarm", "震动启动失败", it) }
    }

    private fun stopAllAndFinish() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        finish()
    }

    private fun completeTask(taskId: Long) {
        lifecycleScope.launch {
            if (taskId > 0) {
                taskDao.getById(taskId)?.let {
                    checkInRepository.checkIn(it)
                    notificationHelper.cancelForTask(it.id)
                }
            }
            AppLog.i("Alarm", "闹钟页完成打卡 task=#$taskId")
            stopAllAndFinish()
        }
    }

    private fun snoozeTask(taskId: Long) {
        if (taskId > 0) scheduler.snooze(taskId)
        AppLog.i("Alarm", "闹钟页稍后提醒 task=#$taskId")
        stopAllAndFinish()
    }

    override fun onDestroy() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEST = "alarm_test"
    }
}

@Composable
private fun AlarmScreen(
    task: TaskEntity?,
    isTest: Boolean,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeText = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14181F)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = timeText,
                color = Color(0xFF9AA4B2),
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isTest) {
                    AppLocale.string(context, R.string.alarm_test_title)
                } else {
                    AppLocale.string(context, R.string.alarm_title)
                },
                color = Color(0xFF7FA6E8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = task?.name ?: AppLocale.string(context, R.string.alarm_default_task),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (task != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = AppLocale.string(context, R.string.alarm_points, task.points),
                    color = Color(0xFF9AA4B2),
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3E6DD6),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    AppLocale.string(context, R.string.alarm_complete),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB8C4D6)),
            ) {
                Text(AppLocale.string(context, R.string.alarm_snooze))
            }
            Spacer(Modifier.height(28.dp))
            TextButton(onClick = onDismiss) {
                Text(
                    AppLocale.string(context, R.string.alarm_dismiss),
                    color = Color(0xFF7A8494),
                )
            }
        }
    }
}
