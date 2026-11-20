package com.malbiseo.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 안드로이드 TextToSpeech 래퍼. 비서 답변을 한국어로 읽어준다.
 * 앱 전체에서 하나만 쓴다 (MalbiseoApp.speaker). 엔진 준비 전에 부르면 준비되는 대로 읽는다.
 */
class Speaker(context: Context) {
    /** 한국어로 읽을 수 있는지. 엔진이 없거나 한국어 음성이 없으면 false */
    var available by mutableStateOf<Boolean?>(null)   // null = 아직 확인 중
        private set

    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

    private fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.KOREAN).let {
            it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED
        }
        available = ok
        if (!ok) Log.w("Speaker", "한국어 TTS 를 쓸 수 없어요 (status=$status)")
        pending?.let { if (ok) speak(it) }
        pending = null
    }

    /** 지금 읽던 것을 끊고 [text] 를 읽는다 */
    fun speak(text: String) {
        val clean = cleanForSpeech(text)
        if (clean.isEmpty()) return
        when (available) {
            null -> pending = clean
            true -> tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "reply")
            false -> Unit
        }
    }

    fun stop() {
        pending = null
        if (available == true) tts.stop()
    }

    /** 목록 기호·따옴표 같은 건 소리 내 읽지 않도록 정리 */
    private fun cleanForSpeech(text: String): String = text
        .replace("•", "")
        .replace("\"", "")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString(". ")
        .replace(":.", ".")
        .trim()
}
