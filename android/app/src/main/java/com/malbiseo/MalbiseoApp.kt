package com.malbiseo

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.malbiseo.ai.ClaudeClient
import com.malbiseo.data.AuthRepository
import com.malbiseo.data.SettingsStore
import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.speech.Speaker
import com.malbiseo.ui.chat.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MalbiseoApp : Application() {
    lateinit var settings: SettingsStore
        private set
    val claude: ClaudeClient by lazy { ClaudeClient() }
    val auth: AuthRepository by lazy { AuthRepository() }
    val speaker: Speaker by lazy { Speaker(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 채팅 기록 (앱이 살아있는 동안 유지) */
    val chatLog = mutableStateListOf<ChatMessage>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        ReminderScheduler.createChannel(this)
        // 자동 로그인을 꺼두었으면 앱을 새로 켤 때마다 로그아웃
        appScope.launch { auth.applyAutoLogin(settings.autoLogin.first()) }
    }
}

fun Context.app(): MalbiseoApp = applicationContext as MalbiseoApp
