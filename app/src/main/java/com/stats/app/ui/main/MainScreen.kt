package com.stats.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stats.app.R
import com.stats.app.ui.settings.SettingsScreen
import com.stats.app.ui.stats.StatsScreen
import com.stats.app.ui.tasks.TaskListScreen

@Composable
fun MainScreen(
    onCreateTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 已在统计页时再次点击"统计"导航键 -> 递增信号，通知统计页回到今天
    var statsBackToToday by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    label = { Text(stringResource(R.string.main_tab_tasks)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = {
                        if (tab == 1) statsBackToToday++ else tab = 1
                    },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.main_tab_stats)) },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.main_tab_settings)) },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> TaskListScreen(onCreate = onCreateTask, onEdit = onEditTask)
                1 -> StatsScreen(backToTodaySignal = statsBackToToday)
                else -> SettingsScreen(
                    onOpenCategories = onOpenCategories,
                    onOpenHistory = onOpenHistory,
                )
            }
        }
    }
}
