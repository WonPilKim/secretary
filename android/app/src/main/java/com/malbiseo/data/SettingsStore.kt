package com.malbiseo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** 이 기기에만 저장하는 설정 (API 키 등). 로그인 상태는 Firebase 가 관리한다. */
class SettingsStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("claude_api_key")
    private val keyModel = stringPreferencesKey("claude_model")

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApi] ?: "" }
    val model: Flow<String> = context.dataStore.data.map { it[keyModel] ?: DEFAULT_MODEL }

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

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}
