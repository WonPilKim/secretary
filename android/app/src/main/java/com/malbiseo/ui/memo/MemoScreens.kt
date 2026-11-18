package com.malbiseo.ui.memo

import com.malbiseo.ui.auth.LocalUserData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malbiseo.app
import com.malbiseo.data.Memo
import com.malbiseo.speech.rememberSpeechController
import com.malbiseo.speech.rememberWithMicPermission
import com.malbiseo.ui.common.AppCard
import com.malbiseo.ui.common.ConfirmDialog
import com.malbiseo.ui.common.EmptyState
import com.malbiseo.ui.common.EmptyText
import com.malbiseo.ui.common.ScreenHeader
import com.malbiseo.ui.common.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun MemoListScreen(onOpen: (Long) -> Unit) {
    val dao = LocalUserData.current.memos
    var q by rememberSaveable { mutableStateOf("") }
    val memos by remember(q) { dao.search(q.trim()) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<Memo?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("메모", subtitle = "생각을 바로 적어두세요")
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (q.isNotEmpty()) IconButton(onClick = { q = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "검색 초기화")
                    }
                },
                placeholder = { Text("메모 검색") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (memos.isEmpty()) {
                if (q.isBlank()) EmptyState(Icons.Filled.EditNote, "저장된 메모가 없어요", "＋ 버튼이나 비서 탭에서 \"메모 ○○\"라고 말해 보세요")
                else EmptyState(Icons.Filled.SearchOff, "\"$q\" 검색 결과가 없어요")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(memos, key = { it.id }) { m ->
                        AppCard(onClick = { onOpen(m.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "작성 ${formatDateTime(m.createdAt)}" +
                                            (m.updatedAt?.let { " · 수정 ${formatDateTime(it)}" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { toDelete = m }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpen(0L) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "새 메모") }
    }

    toDelete?.let { m ->
        ConfirmDialog(
            title = "메모 삭제",
            text = "정말 삭제할까요?",
            onConfirm = { scope.launch { dao.delete(m) }; toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
fun MemoEditScreen(id: Long, onDone: () -> Unit) {
    val dao = LocalUserData.current.memos
    val isNew = id == 0L
    var text by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Memo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        if (!isNew) {
            val m = dao.get(id)
            original = m
            if (!loaded && m != null) text = m.text
        }
        loaded = true
    }

    val speech = rememberSpeechController { heard ->
        text = if (text.isBlank()) heard else text + "\n" + heard
    }
    val startMic = rememberWithMicPermission { speech.start() }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(if (isNew) "메모 작성" else "메모 수정", onBack = onDone)
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                placeholder = { Text("메모 내용을 입력하세요.") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
            if (speech.listening) {
                Text("듣는 중… ${speech.partial}", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { if (speech.listening) speech.stop() else startMic() }) {
                    Icon(if (speech.listening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (speech.listening) "멈추기" else "음성으로 입력")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val t = text.trim()
                    if (t.isEmpty()) {
                        error = "내용을 입력해 주세요."
                        return@Button
                    }
                    scope.launch {
                        val o = original
                        if (o == null) dao.insert(Memo(text = t))
                        else dao.update(o.copy(text = t, updatedAt = System.currentTimeMillis()))
                        onDone()
                    }
                }) { Text(if (isNew) "저장" else "수정 완료") }
            }
        }
    }
}
