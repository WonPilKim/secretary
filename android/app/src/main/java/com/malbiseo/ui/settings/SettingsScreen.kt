package com.malbiseo.ui.settings

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.ui.auth.LocalUserData
import com.malbiseo.ui.common.ConfirmDialog
import com.malbiseo.ui.common.AppCard
import com.malbiseo.ui.auth.LocalUser
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.malbiseo.app
import com.malbiseo.data.SettingsStore
import com.malbiseo.ui.common.ScreenHeader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = context.app().settings
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(SettingsStore.DEFAULT_MODEL) }
    var showKey by remember { mutableStateOf(false) }
    var askLogout by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    val user = LocalUser.current
    val data = LocalUserData.current

    LaunchedEffect(Unit) {
        apiKey = settings.apiKey.first()
        model = settings.model.first()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("설정")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 계정
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            user.name.take(1),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${user.name}님", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { nameDraft = user.name; editName = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "이름 변경", modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            "아이디 ${user.loginId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { askLogout = true }) { Text("로그아웃") }
                }
            }

            HorizontalDivider()

            Text("회의록 AI 요약", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Claude API 키") },
                placeholder = { Text("sk-ant-...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "키 보기",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("모델") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "API 키는 console.anthropic.com 에서 발급받을 수 있어요. 키는 이 기기에만 저장됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        settings.save(apiKey, model)
                        Toast.makeText(context, "저장했어요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }

            HorizontalDivider()

            Text("음성 인식", style = MaterialTheme.typography.titleMedium)
            Text(
                if (SpeechRecognizer.isRecognitionAvailable(context))
                    "사용 가능 (한국어, ko-KR)"
                else
                    "사용 불가 — Google 앱 또는 음성 인식 서비스를 설치/업데이트해 주세요.",
            )
        }
    }

    if (askLogout) {
        ConfirmDialog(
            title = "로그아웃",
            text = "로그아웃할까요? 메모·회의록·일정은 계정에 안전하게 남아 있고, 다시 로그인하면 그대로 볼 수 있어요.",
            confirmLabel = "로그아웃",
            onConfirm = {
                askLogout = false
                scope.launch {
                    // 이 폰에 예약된 내 일정 알림을 지운 뒤 로그아웃
                    runCatching {
                        data.schedules.upcoming(System.currentTimeMillis())
                            .forEach { ReminderScheduler.cancel(context, it.id) }
                    }
                    context.app().chatLog.clear()
                    context.app().auth.logout()
                }
            },
            onDismiss = { askLogout = false },
        )
    }

    if (editName) {
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("이름 변경") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("이름") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            context.app().auth.updateName(nameDraft)
                            editName = false
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "이름을 바꾸지 못했어요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("취소") } },
        )
    }
}
