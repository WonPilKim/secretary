package com.malbiseo.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 안드로이드 SpeechRecognizer 래퍼 (기존 웹의 Web Speech API 대체).
 *
 * continuous = false : 한 문장 듣고 끝 (메모·채팅 입력용)
 * continuous = true  : 멈출 때까지 계속 재시작 (회의 녹취용)
 */
class SpeechController(
    private val context: Context,
    private val continuous: Boolean,
) {
    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)

    var onFinal: (String) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (continuous) {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
    }

    fun start() {
        if (wantListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "이 기기에서는 음성 인식을 사용할 수 없어요. (Google 앱 설치/업데이트 확인)"
            return
        }
        wantListening = true
        listening = true
        partial = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        r.startListening(intent)
    }

    /** 듣기 종료. 마지막 결과는 onResults 로 전달된다. */
    fun stop() {
        wantListening = false
        recognizer?.stopListening()
        listening = false
    }

    fun release() {
        wantListening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun restartLater(delayMs: Long = 250) {
        handler.postDelayed({
            if (wantListening) recognizer?.startListening(intent)
        }, delayMs)
    }

    private fun finishSession() {
        if (continuous && wantListening) {
            restartLater()
        } else {
            wantListening = false
            listening = false
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
            partial = ""
            if (text.isNotEmpty()) onFinal(text)
            finishSession()
        }

        override fun onError(code: Int) {
            partial = ""
            when (code) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    error = "마이크 권한이 필요해요."
                    wantListening = false
                    listening = false
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (!continuous && wantListening) error = "말씀을 인식하지 못했어요. 다시 시도해 주세요."
                    finishSession()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    if (continuous && wantListening) {
                        recognizer?.cancel()
                        restartLater(600)
                    } else finishSession()
                }
                SpeechRecognizer.ERROR_CLIENT -> finishSession() // stop() 직후 정상적으로 올 수 있음
                else -> {
                    if (!continuous) error = "음성 인식 오류가 발생했어요. (코드 $code)"
                    if (continuous && wantListening) restartLater(1000) else finishSession()
                }
            }
        }
    }
}

@Composable
fun rememberSpeechController(
    continuous: Boolean = false,
    onFinal: (String) -> Unit,
): SpeechController {
    val context = LocalContext.current
    val controller = remember { SpeechController(context, continuous) }
    SideEffect { controller.onFinal = onFinal }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LaunchedEffect(controller.error) {
        controller.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            controller.error = null
        }
    }
    return controller
}

/** 마이크 권한을 확인(필요하면 요청)한 뒤 onGranted 실행 */
@Composable
fun rememberWithMicPermission(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onGranted)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latest()
        else Toast.makeText(context, "음성 인식을 쓰려면 마이크 권한이 필요해요.", Toast.LENGTH_SHORT).show()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) latest() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
