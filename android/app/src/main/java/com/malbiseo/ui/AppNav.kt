package com.malbiseo.ui

import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.ui.auth.LocalUserData
import com.malbiseo.data.UserData
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.malbiseo.ui.auth.LocalUser
import com.malbiseo.ui.auth.AuthScreen
import com.malbiseo.data.AuthState
import com.malbiseo.app
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.malbiseo.ui.chat.ChatScreen
import com.malbiseo.ui.meeting.MeetingDetailScreen
import com.malbiseo.ui.meeting.MeetingListScreen
import com.malbiseo.ui.meeting.MeetingRecordScreen
import com.malbiseo.ui.memo.MemoEditScreen
import com.malbiseo.ui.memo.MemoListScreen
import com.malbiseo.ui.schedule.ScheduleEditScreen
import com.malbiseo.ui.schedule.ScheduleScreen
import com.malbiseo.ui.settings.SettingsScreen

object Routes {
    const val CHAT = "chat"
    const val MEMOS = "memos"
    const val MEETINGS = "meetings"
    const val SCHEDULE = "schedule"
    const val SETTINGS = "settings"
    const val MEETING_RECORD = "meeting_record"

    fun memo(id: Long) = "memo/$id"
    fun meeting(id: Long) = "meeting/$id"
    fun scheduleEdit(id: Long, epochDay: Long = -1L) = "schedule_edit/$id?date=$epochDay"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.CHAT, "비서", Icons.AutoMirrored.Filled.Chat),
    Tab(Routes.MEMOS, "메모", Icons.Filled.StickyNote2),
    Tab(Routes.MEETINGS, "회의록", Icons.Filled.RecordVoiceOver),
    Tab(Routes.SCHEDULE, "일정", Icons.Filled.Event),
    Tab(Routes.SETTINGS, "설정", Icons.Filled.Settings),
)

private fun NavController.go(route: String) {
    if (tabs.any { it.route == route }) {
        navigate(route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route)
    }
}

/** 로그인 여부에 따라 로그인 화면 또는 앱 본화면 */
@Composable
fun AppRoot() {
    val app = LocalContext.current.app()
    val state by remember { app.auth.state }.collectAsState(initial = AuthState.Loading)
    when (val s = state) {
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AuthState.LoggedOut -> AuthScreen()
        is AuthState.LoggedIn -> key(s.user.id) {   // 계정이 바뀌면 화면 상태를 새로 시작
            val data = remember { UserData(s.user.id) }
            val context = LocalContext.current
            // 새 폰에서 로그인해도 남은 일정 알림이 울리도록 다시 예약
            LaunchedEffect(Unit) {
                runCatching {
                    data.schedules.upcoming(System.currentTimeMillis())
                        .forEach { ReminderScheduler.schedule(context, it) }
                }
            }
            CompositionLocalProvider(LocalUser provides s.user, LocalUserData provides data) { AppNav() }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val showBottomBar = tabs.any { it.route == current?.route }

    RequestNotificationPermissionOnce()

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = { nav.go(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CHAT, modifier = Modifier.padding(padding)) {
            composable(Routes.CHAT) {
                ChatScreen(onNavigate = { nav.go(it) })
            }
            composable(Routes.MEMOS) {
                MemoListScreen(onOpen = { nav.navigate(Routes.memo(it)) })
            }
            composable(
                "memo/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                MemoEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.MEETINGS) {
                MeetingListScreen(
                    onOpen = { nav.navigate(Routes.meeting(it)) },
                    onRecord = { nav.navigate(Routes.MEETING_RECORD) },
                )
            }
            composable(Routes.MEETING_RECORD) {
                MeetingRecordScreen(
                    onSaved = { id ->
                        nav.navigate(Routes.meeting(id)) {
                            popUpTo(Routes.MEETING_RECORD) { inclusive = true }
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "meeting/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                MeetingDetailScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SCHEDULE) {
                ScheduleScreen(onOpen = { id, day -> nav.navigate(Routes.scheduleEdit(id, day)) })
            }
            composable(
                "schedule_edit/{id}?date={date}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                ScheduleEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialEpochDay = entry.arguments?.getLong("date") ?: -1L,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
