package com.malbiseo.ui.auth

import com.malbiseo.data.UserData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malbiseo.app
import com.malbiseo.data.AuthException
import com.malbiseo.data.User
import kotlinx.coroutines.launch

/** 로그인한 사용자. 로그인 후 화면 어디서나 LocalUser.current 로 읽는다. */
val LocalUser = staticCompositionLocalOf<User> { error("로그인한 사용자가 없어요") }

/** 로그인한 사용자의 클라우드 데이터 (메모·회의록·일정) */
val LocalUserData = staticCompositionLocalOf<UserData> { error("로그인한 사용자가 없어요") }

@Composable
fun AuthScreen() {
    val app = LocalContext.current.app()
    val auth = app.auth
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var loginId by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var autoLogin by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }

    // 마지막으로 쓴 이메일과 자동 로그인 선택을 불러온다
    LaunchedEffect(Unit) {
        if (loginId.isEmpty()) loginId = app.settings.lastLoginId.first()
        autoLogin = app.settings.autoLogin.first()
    }

    fun submit() {
        if (busy) return
        focus.clearFocus()
        busy = true
        error = null
        scope.launch {
            try {
                // 로그인되면 이 화면이 바로 사라지므로 설정은 먼저 저장한다
                app.settings.saveLogin(autoLogin, loginId)
                if (signUp) auth.signUp(name, loginId, password, confirm)
                else auth.login(loginId, password)
            } catch (e: AuthException) {
                error = e.message
            } catch (e: Exception) {
                error = "문제가 생겼어요. 다시 시도해 주세요."
            } finally {
                busy = false
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    )
    val fieldShape = RoundedCornerShape(16.dp)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("말", color = MaterialTheme.colorScheme.onPrimary, fontSize = 48.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text("말비서", style = MaterialTheme.typography.headlineMedium)
        Text(
            "말하면, 정리됩니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        // 로그인 / 회원가입 전환
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(4.dp)) {
                listOf(false to "로그인", true to "회원가입").forEach { (mode, label) ->
                    val selected = signUp == mode
                    Surface(
                        onClick = { signUp = mode; error = null },
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = if (selected) 1.dp else 0.dp,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            label,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (signUp) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text("이름") }, singleLine = true,
                    shape = fieldShape, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = loginId, onValueChange = { loginId = it.trim(); error = null },
                label = { Text("이메일") }, singleLine = true,
                supportingText = {
                    Text(
                        if (signUp) "비밀번호를 잊었을 때 이 주소로 재설정 메일을 보내요"
                        else "예전에 아이디로 가입했다면 아이디를 입력하세요"
                    )
                },
                shape = fieldShape, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = null },
                label = { Text("비밀번호") }, singleLine = true,
                supportingText = if (signUp) {
                    { Text("6자 이상") }
                } else null,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "비밀번호 보기")
                    }
                },
                shape = fieldShape, colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (signUp) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (signUp) {
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text("비밀번호 확인") }, singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && confirm != password,
                    shape = fieldShape, colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 자동 로그인 / 비밀번호 찾기
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).toggleable(
                    value = autoLogin, role = Role.Checkbox, onValueChange = { autoLogin = it },
                ).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = autoLogin, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Text("자동 로그인", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.weight(1f))
            if (!signUp) {
                TextButton(onClick = { showReset = true; error = null }) { Text("비밀번호 찾기") }
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { submit() },
            enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (signUp) "가입하고 시작하기" else "로그인", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "로그인하면 어느 폰에서든 내 메모·회의록·일정을 볼 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showReset) {
        PasswordResetDialog(initialEmail = loginId, onDismiss = { showReset = false })
    }
}

/** 이메일을 받아 비밀번호 재설정 메일을 보낸다 */
@Composable
private fun PasswordResetDialog(initialEmail: String, onDismiss: () -> Unit) {
    val auth = LocalContext.current.app().auth
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(initialEmail.takeIf { '@' in it } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀번호 찾기") },
        text = {
            if (sent) {
                Text("$email 주소로 가입되어 있다면 비밀번호 재설정 메일을 보냈어요.\n메일의 링크에서 새 비밀번호를 정한 뒤 다시 로그인해 주세요. 메일이 안 보이면 스팸함도 확인해 주세요.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("가입한 이메일을 입력하면 비밀번호를 새로 정할 수 있는 링크를 보내드려요.")
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.trim(); error = null },
                        label = { Text("이메일") }, singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("확인") }
            } else {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            auth.sendPasswordReset(email)
                            sent = true
                        } catch (e: AuthException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "보내는 중…" else "메일 보내기") }
            }
        },
        dismissButton = if (sent) null else {
            { TextButton(onClick = onDismiss) { Text("취소") } }
        },
    )
}
