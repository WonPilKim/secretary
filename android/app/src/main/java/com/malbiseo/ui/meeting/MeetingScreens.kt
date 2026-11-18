package com.malbiseo.ui.meeting

import com.malbiseo.ui.auth.LocalUserData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malbiseo.app
import com.malbiseo.data.Meeting
import com.malbiseo.speech.rememberSpeechController
import com.malbiseo.speech.rememberWithMicPermission
import com.malbiseo.ui.common.AppCard
import com.malbiseo.ui.common.ConfirmDialog
import com.malbiseo.ui.common.EmptyState
import com.malbiseo.ui.common.IconTile
import com.malbiseo.ui.common.Pill
import com.malbiseo.ui.common.formatShortDateTime
import com.malbiseo.ui.common.EmptyText
import com.malbiseo.ui.common.ScreenHeader
import com.malbiseo.ui.common.formatDateTime
import com.malbiseo.ui.common.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ───────────────────────── 목록 ─────────────────────────

@Composable
fun MeetingListScreen(onOpen: (Long) -> Unit, onRecord: () -> Unit) {
    val dao = LocalUserData.current.meetings
    val meetings by remember { dao.all() }.collectAsState(initial = emptyList())

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("회의록", subtitle = "받아쓰고, 요약하고")
            if (meetings.isEmpty()) {
                EmptyState(
                    Icons.Filled.RecordVoiceOver,
                    "아직 회의록이 없어요",
                    "회의 기록을 누르면 말하는 내용을 받아 적고\nAI가 핵심만 정리해 드려요",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(meetings, key = { it.id }) { m ->
                        AppCard(onClick = { onOpen(m.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                IconTile(
                                    if (m.summary != null) Icons.Filled.AutoAwesome else Icons.Filled.Mic,
                                    container = if (m.summary != null) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    tint = if (m.summary != null) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            m.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (m.summary != null) {
                                            Spacer(Modifier.width(6.dp))
                                            Pill("AI 요약")
                                        }
                                    }
                                    Text(
                                        "${formatShortDateTime(m.createdAt)} · ${formatDuration(m.durationSec)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        (m.summary ?: m.transcript).replace(Regex("^■.*\\n?"), "").trim(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onRecord,
            icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
            text = { Text("회의 기록") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

// ───────────────────────── 녹취 ─────────────────────────

@Composable
fun MeetingRecordScreen(onSaved: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = LocalUserData.current.meetings
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("회의 " + formatDateTime(System.currentTimeMillis())) }
    val lines = remember { mutableStateListOf<String>() }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var askDiscard by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val speech = rememberSpeechController(continuous = true) { heard ->
        lines.add("[${formatDuration(elapsed)}] $heard")
    }
    val startMic = rememberWithMicPermission { speech.start() }

    // 녹취 중에는 화면이 꺼지지 않게 (꺼지면 음성 인식이 멈춤)
    val view = LocalView.current
    DisposableEffect(speech.listening) {
        view.keepScreenOn = speech.listening
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(speech.listening) {
        while (speech.listening) {
            delay(1000)
            elapsed++
        }
    }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    BackHandler(enabled = lines.isNotEmpty()) { askDiscard = true }

    fun save() {
        speech.stop()
        if (lines.isEmpty()) {
            Toast.makeText(context, "기록된 내용이 없어요.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            delay(300) // stop() 직후 마지막 문장이 들어올 시간
            val id = dao.insert(
                Meeting(
                    title = title.ifBlank { "제목 없는 회의" },
                    transcript = lines.joinToString("\n"),
                    durationSec = elapsed,
                )
            )
            onSaved(id)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("회의 기록", onBack = { if (lines.isNotEmpty()) askDiscard = true else onBack() })
        Column(Modifier.padding(horizontal = 16.dp).weight(1f)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("회의 제목") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(elapsed), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(12.dp))
                if (speech.listening) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("듣는 중", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        if (lines.isEmpty()) "시작을 누르세요" else "일시정지됨",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(lines) { Text(it) }
                if (speech.partial.isNotEmpty()) {
                    item {
                        Text(speech.partial, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (speech.listening) speech.stop() else startMic() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(if (speech.listening) Icons.Filled.Pause else Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (speech.listening) "일시정지" else if (lines.isEmpty()) "시작" else "계속")
            }
            Button(onClick = { save() }, modifier = Modifier.weight(1f)) { Text("종료 및 저장") }
        }
    }

    if (askDiscard) {
        ConfirmDialog(
            title = "기록 취소",
            text = "저장하지 않은 회의 기록이 사라져요. 나갈까요?",
            confirmLabel = "나가기",
            onConfirm = { askDiscard = false; speech.release(); onBack() },
            onDismiss = { askDiscard = false },
        )
    }
}

// ───────────────────────── 상세 / 요약 ─────────────────────────

@Composable
fun MeetingDetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val dao = LocalUserData.current.meetings
    val meeting by remember(id) { dao.observe(id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var summarizing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }

    val m = meeting ?: run {
        Column { ScreenHeader("회의록", onBack = onBack) }
        return
    }

    fun summarize() {
        summarizing = true
        error = null
        scope.launch {
            try {
                val summary = app.claude.summarizeMeeting(
                    apiKey = app.settings.apiKey.first(),
                    model = app.settings.model.first(),
                    title = m.title,
                    transcript = m.transcript,
                )
                dao.update(m.copy(summary = summary))
            } catch (e: Exception) {
                error = e.message ?: "요약 중 오류가 발생했어요."
            } finally {
                summarizing = false
            }
        }
    }

    fun share() {
        val text = buildString {
            appendLine(m.title)
            appendLine(formatDateTime(m.createdAt))
            appendLine()
            m.summary?.let { appendLine(it); appendLine() }
            appendLine("— 녹취록 —")
            append(m.transcript)
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "회의록 공유"))
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(m.title, onBack = onBack) {
            IconButton(onClick = { share() }) { Icon(Icons.Filled.Share, contentDescription = "공유") }
            IconButton(onClick = { askDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                "${formatDateTime(m.createdAt)} · ${formatDuration(m.durationSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 요약
            AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI 요약", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (summarizing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            AssistChip(
                                onClick = { summarize() },
                                label = { Text(if (m.summary == null) "요약하기" else "다시 요약") },
                                leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            )
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    m.summary?.let {
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(it) }
                    }
                    if (m.summary == null && error == null && !summarizing) {
                        Text(
                            "버튼을 누르면 논의 내용·결정 사항·할 일을 정리해 드려요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 녹취록
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("녹취록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (editing) {
                    TextButton(onClick = { editing = false }) { Text("취소") }
                    TextButton(onClick = {
                        scope.launch { dao.update(m.copy(transcript = draft)) }
                        editing = false
                    }) { Text("저장") }
                } else {
                    IconButton(onClick = { draft = m.transcript; editing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "녹취록 수정")
                    }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                )
            } else {
                SelectionContainer { Text(m.transcript) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = "회의록 삭제",
            text = "이 회의록을 삭제할까요? 되돌릴 수 없어요.",
            onConfirm = {
                askDelete = false
                scope.launch { dao.delete(m); onBack() }
            },
            onDismiss = { askDelete = false },
        )
    }
}
