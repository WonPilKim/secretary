package com.malbiseo.speech

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
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
import androidx.compose.runtime.mutableFloatStateOf
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
 *
 * 연속 모드에서는 문장마다 인식기를 새로 만든다. 같은 인식기를 재사용하면
 * 많은 기기에서 BUSY/CLIENT 오류가 반복되며 아무것도 인식되지 않는 문제가 있다.
 */
class SpeechController(
    private val context: Context,
    private val continuous: Boolean,
) {
    /** 사용자가 듣기를 켜 둔 상태 (연속 모드에서 문장 사이 재시작 중에도 true) */
    var listening by mutableStateOf(false)
        private set
    /** 지금 인식 중인(아직 확정되지 않은) 문장 — 실시간 표시용 */
    var partial by mutableStateOf("")
        private set
    /** 마이크가 실제로 소리를 받을 준비가 됐는지 */
    var ready by mutableStateOf(false)
        private set
    /** 입력 음량 0f..1f (레벨 미터용) */
    var level by mutableFloatStateOf(0f)
        private set
    /** stop() 후 마지막 문장 확정을 기다리는 중 */
    var finalizing by mutableStateOf(false)
        private set
    /** 연속 모드에서 계속 표시할 상태 메시지 (오류 등) */
    var status by mutableStateOf<String?>(null)
        private set
    /** 토스트로 한 번 보여줄 오류 */
    var error by mutableStateOf<String?>(null)

    var onFinal: (String) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var session = 0
    private var consecutiveErrors = 0

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (continuous) {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }
    }

    fun start() {
        if (wantListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "이 기기에서는 음성 인식을 사용할 수 없어요. (Google 앱 설치/업데이트 확인)"
            return
        }
        handler.removeCallbacksAndMessages(null)
        wantListening = true
        listening = true
        finalizing = false
        partial = ""
        status = null
        consecutiveErrors = 0
        startSession()
    }

    /** 듣기 종료. 마지막 문장은 결과가 오면 확정, 안 오면 부분 결과로 확정한다. */
    fun stop() {
        if (!wantListening && !listening) return
        wantListening = false
        listening = false
        ready = false
        level = 0f
        handler.removeCallbacksAndMessages(null)
        val r = recognizer ?: run { partial = ""; return }
        finalizing = true
        r.stopListening()
        // 일부 기기는 stopListening 후 onResults 를 주지 않음 → 부분 결과로 확정
        val s = session
        handler.postDelayed({ if (s == session && finalizing) commitPartialAndClose() }, 1500)
    }

    fun release() {
        wantListening = false
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        listening = false
        ready = false
        finalizing = false
        partial = ""
        level = 0f
    }

    private fun commitPartialAndClose() {
        val text = partial.trim()
        partial = ""
        finalizing = false
        if (text.isNotEmpty()) onFinal(text)
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        session++ // 이전 인식기의 늦은 콜백 무시
        recognizer?.let {
            it.setRecognitionListener(null)
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun startSession() {
        destroyRecognizer()
        val s = session
        val r = createRecognizer(context)
        r.setRecognitionListener(Listener(s))
        recognizer = r
        ready = false
        runCatching { r.startListening(intent) }.onFailure {
            status = "음성 인식을 시작하지 못했어요."
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.postDelayed({ if (wantListening) startSession() }, delayMs)
    }

    /** 한 문장(세션)이 끝났을 때 */
    private fun sessionEnded() {
        ready = false
        level = 0f
        if (finalizing) {
            finalizing = false
            partial = ""
            destroyRecognizer()
            return
        }
        if (continuous && wantListening) {
            scheduleRestart(100)
        } else {
            wantListening = false
            listening = false
            destroyRecognizer()
        }
    }

    private fun fatal(message: String) {
        error = message
        status = message
        wantListening = false
        listening = false
        finalizing = false
        partial = ""
        ready = false
        destroyRecognizer()
    }

    private inner class Listener(private val s: Int) : RecognitionListener {
        private fun stale() = s != session

        override fun onReadyForSpeech(params: Bundle?) {
            if (stale()) return
            ready = true
            status = null
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            if (stale()) return
            // 대략 -2..10 dB 범위를 0..1 로
            level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (stale()) return
            level = 0f
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (stale()) return
            val stable = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            // 일부 Google 엔진은 아직 불안정한 뒷부분을 따로 준다
            val unstable = partialResults
                ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                ?.firstOrNull().orEmpty()
            val text = (stable + unstable).trim()
            if (text.isNotEmpty()) partial = text
        }

        override fun onResults(results: Bundle?) {
            if (stale()) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty().trim()
                .ifEmpty { partial.trim() } // 최종 결과가 비어 오면 부분 결과라도 살림
            partial = ""
            consecutiveErrors = 0
            if (text.isNotEmpty()) onFinal(text)
            sessionEnded()
        }

        override fun onError(code: Int) {
            if (stale()) return
            // 오류로 끝나도 듣던 문장은 버리지 않는다
            val pending = partial.trim()
            partial = ""
            if (pending.isNotEmpty()) onFinal(pending)

            when (code) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    return fatal("마이크 권한이 필요해요.")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    return fatal("한국어 음성 인식을 사용할 수 없어요. Google 앱의 음성 언어 설정을 확인해 주세요.")
            }

            if (finalizing) { sessionEnded(); return }

            if (!continuous) {
                when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (pending.isEmpty() && wantListening) error = "말씀을 인식하지 못했어요. 다시 시도해 주세요."
                    SpeechRecognizer.ERROR_CLIENT -> {} // stop() 직후 정상적으로 올 수 있음
                    else -> error = "음성 인식 오류가 발생했어요. (${describe(code)})"
                }
                sessionEnded()
                return
            }

            // 연속 모드: 조용해서 끝난 경우는 정상 → 바로 재시작
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrors = 0
                    sessionEnded()
                }
                else -> {
                    consecutiveErrors++
                    status = "음성 인식 재연결 중… (${describe(code)})"
                    if (consecutiveErrors >= 8) {
                        fatal("음성 인식이 계속 실패해요. (${describe(code)}) 네트워크나 Google 앱을 확인해 주세요.")
                    } else {
                        ready = false
                        level = 0f
                        destroyRecognizer()
                        scheduleRestart(minOf(500L * consecutiveErrors, 3000L))
                    }
                }
            }
        }
    }

    companion object {
        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"

        /** 가능하면 Google 음성 인식 서비스를 사용 (일부 제조사 기본 엔진은 부분 결과를 주지 않음) */
        fun createRecognizer(context: Context): SpeechRecognizer {
            val services = context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE), 0,
            )
            val google = services.firstOrNull { it.serviceInfo.packageName == GOOGLE_PACKAGE }
            return if (google != null) {
                SpeechRecognizer.createSpeechRecognizer(
                    context,
                    ComponentName(google.serviceInfo.packageName, google.serviceInfo.name),
                )
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }

        fun describe(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK -> "네트워크 오류"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 시간 초과"
            SpeechRecognizer.ERROR_AUDIO -> "마이크 오류"
            SpeechRecognizer.ERROR_SERVER -> "서버 오류"
            SpeechRecognizer.ERROR_CLIENT -> "클라이언트 오류"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 사용 중"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "요청 과다"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "서버 연결 끊김"
            else -> "코드 $code"
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
