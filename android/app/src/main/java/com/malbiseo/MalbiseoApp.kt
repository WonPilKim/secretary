package com.malbiseo

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.malbiseo.ai.ClaudeClient
import com.malbiseo.data.AuthRepository
import com.malbiseo.data.SettingsStore
import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.ui.chat.ChatMessage

class MalbiseoApp : Application() {
    lateinit var settings: SettingsStore
        private set
    val claude: ClaudeClient by lazy { ClaudeClient() }
    val auth: AuthRepository by lazy { AuthRepository() }

    /** 채팅 기록 (앱이 살아있는 동안 유지) */
    val chatLog = mutableStateListOf<ChatMessage>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        ReminderScheduler.createChannel(this)
    }
}

fun Context.app(): MalbiseoApp = applicationContext as MalbiseoApp
