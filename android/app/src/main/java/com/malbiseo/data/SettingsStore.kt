package com.malbiseo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 이 기기에만 저장하는 설정 (API 키, 자동 로그인 등). 로그인 자체는 Firebase 가 관리한다. */
class SettingsStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("claude_api_key")
    private val keyModel = stringPreferencesKey("claude_model")
    private val keyAutoLogin = booleanPreferencesKey("auto_login")
    private val keyLastLogin = stringPreferencesKey("last_login_id")
    private val keySpeak = booleanPreferencesKey("speak_replies")
    private val keySpeakTyped = booleanPreferencesKey("speak_typed")

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApi] ?: "" }
    val model: Flow<String> = context.dataStore.data.map { it[keyModel] ?: DEFAULT_MODEL }

    /** 앱을 다시 켜도 로그인을 유지할지 (기본: 유지) */
    val autoLogin: Flow<Boolean> = context.dataStore.data.map { it[keyAutoLogin] ?: true }

    /** 마지막으로 로그인한 이메일(아이디). 로그인 화면에 미리 채운다. */
    val lastLoginId: Flow<String> = context.dataStore.data.map { it[keyLastLogin] ?: "" }

    /** 비서 답변을 소리로 읽어줄지 (기본: 켬) */
    val speakReplies: Flow<Boolean> = context.dataStore.data.map { it[keySpeak] ?: true }

    /** 글자로 입력했을 때도 읽어줄지 (기본: 끔, 음성으로 물었을 때만 읽음) */
    val speakTyped: Flow<Boolean> = context.dataStore.data.map { it[keySpeakTyped] ?: false }

    suspend fun setSpeakReplies(on: Boolean) = context.dataStore.edit { it[keySpeak] = on }
    suspend fun setSpeakTyped(on: Boolean) = context.dataStore.edit { it[keySpeakTyped] = on }

    suspend fun save(apiKey: String, model: String) {
        context.dataStore.edit {
            it[keyApi] = apiKey.trim()
            it[keyModel] = model.trim().ifEmpty { DEFAULT_MODEL }
        }
    }

    /** 이 기기에 저장된 설정 모두 지우기 (회원 탈퇴 시) */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveLogin(autoLogin: Boolean, loginId: String) {
        context.dataStore.edit {
            it[keyAutoLogin] = autoLogin
            it[keyLastLogin] = loginId.trim().lowercase()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
