package com.malbiseo.ui.chat

import com.malbiseo.ui.auth.LocalUserData
import com.malbiseo.ui.auth.LocalUser
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malbiseo.app
import com.malbiseo.speech.rememberSpeechController
import com.malbiseo.speech.rememberWithMicPermission
import com.malbiseo.ui.Routes
import com.malbiseo.ui.common.AppCard
import com.malbiseo.ui.common.IconTile
import com.malbiseo.ui.common.ScreenHeader
import com.malbiseo.ui.common.endMillis
import com.malbiseo.ui.common.formatDate
import com.malbiseo.ui.common.formatTime
import com.malbiseo.ui.common.startMillis
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val quickCommands = listOf("오늘 일정", "내일 일정", "회의 시작", "메모", "지금 몇 시야")

@Composable
fun ChatScreen(onNavigate: (String) -> Unit) {
    val app = LocalContext.current.app()
    val user = LocalUser.current
    val data = LocalUserData.current
    val context = LocalContext.current
    val handler = remember(data) { CommandHandler(data, context) }
    val messages = app.chatLog
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val today = LocalDate.now()
    val todaySchedules by remember {
        data.schedules.between(today.startMillis(), today.endMillis())
    }.collectAsState(initial = emptyList())
    val next = todaySchedules.firstOrNull { !it.done && it.startAt >= System.currentTimeMillis() }

    val speakReplies by remember { app.settings.speakReplies }.collectAsState(initial = true)
    val speakTyped by remember { app.settings.speakTyped }.collectAsState(initial = false)

    /** [byVoice]: 마이크로 말한 명령인지. 설정에 따라 답변을 소리로 읽어준다 */
    fun send(raw: String, byVoice: Boolean = false) {
        val text = raw.trim()
        if (text.isEmpty()) return
        messages.add(ChatMessage(text, fromUser = true))
        input = ""
        scope.launch {
            val r = handler.handle(text)
            val reply = when (r) {
                is CommandResult.Reply -> r.text
                is CommandResult.Navigate -> r.text
            }
            messages.add(ChatMessage(reply, fromUser = false))
            if (speakReplies && (byVoice || speakTyped)) app.speaker.speak(reply)
            if (r is CommandResult.Navigate) onNavigate(r.route)
        }
    }

    val speech = rememberSpeechController { send(it, byVoice = true) }
    // 읽어주는 소리를 마이크가 받아 적지 않도록 듣기 전에 멈춘다
    val startMic = rememberWithMicPermission { app.speaker.stop(); speech.start() }

    // 첫 인사말: "원앤님, 무엇을 도와드릴까요?"
    // 가입 직후엔 이름이 조금 늦게 들어오므로, 이름이 바뀌면 아직 대화가 없는 인사말도 새 이름으로 바꾼다.
    LaunchedEffect(user.name) {
        val hello = ChatMessage(greetingFor(user.name), false)
        when {
            messages.isEmpty() -> messages.add(hello)
            messages.size == 1 && !messages[0].fromUser && messages[0].text.endsWith(GREETING_TAIL) -> messages[0] = hello
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("${user.name}님", subtitle = "${formatDate(today)} · ${greeting()}")

        // 오늘 일정 요약 카드
        AppCard(
            onClick = { onNavigate(Routes.SCHEDULE) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    Icons.Filled.Event,
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    size = 40.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (todaySchedules.isEmpty()) "오늘은 일정이 없어요"
                        else "오늘 일정 ${todaySchedules.size}개 · 완료 ${todaySchedules.count { it.done }}개",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        next?.let { "다음 · ${formatTime(it.startAt)} ${it.title}" } ?: "여유로운 하루 보내세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { msg ->
                // 비서 말풍선을 누르면 다시 읽어준다
                Bubble(msg, onClick = if (msg.fromUser) null else ({ app.speaker.speak(msg.text) }))
            }
        }

        // 빠른 명령
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickCommands.forEach { cmd ->
                Surface(
                    onClick = { send(cmd) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        cmd,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (speech.listening) ListeningPill(speech.partial)

        // 입력창: 글자가 있으면 보내기, 없으면 마이크
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("말비서에게 부탁하기") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
            )
            Spacer(Modifier.width(8.dp))
            val hasText = input.isNotBlank()
            FilledIconButton(
                onClick = {
                    when {
                        hasText -> send(input)
                        speech.listening -> speech.stop()
                        else -> startMic()
                    }
                },
                modifier = Modifier.size(54.dp),
            ) {
                Icon(
                    when {
                        hasText -> Icons.AutoMirrored.Filled.Send
                        speech.listening -> Icons.Filled.Stop
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = if (hasText) "보내기" else "음성 입력",
                )
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "좋은 아침이에요"
    in 11..16 -> "좋은 오후예요"
    in 17..21 -> "편안한 저녁이에요"
    else -> "오늘도 수고했어요"
}

@Composable
private fun ListeningPill(partial: String) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(alpha).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                if (partial.isBlank()) "듣고 있어요…" else partial,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!msg.fromUser) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("말", color = colors.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (msg.fromUser) colors.primary else colors.surface,
            contentColor = if (msg.fromUser) colors.onPrimary else colors.onSurface,
            shape = if (msg.fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            border = if (msg.fromUser) null else BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.widthIn(max = 290.dp)
                .then(if (onClick != null) Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else Modifier),
        ) {
            Text(msg.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

private const val GREETING_TAIL = "무엇을 도와드릴까요? 마이크를 누르고 편하게 말씀해 주세요."

/** 이름이 있으면 "원앤님, ..." / 아직 없으면(또는 이메일이면) 이름 없이 */
private fun greetingFor(name: String): String =
    if (name.isBlank() || name == "회원" || '@' in name) GREETING_TAIL else "${name}님, $GREETING_TAIL"
