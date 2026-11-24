package com.malbiseo.ui.schedule

import com.malbiseo.ui.auth.LocalUserData
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malbiseo.app
import com.malbiseo.data.Schedule
import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.speech.rememberSpeechController
import com.malbiseo.speech.rememberWithMicPermission
import com.malbiseo.ui.common.AppCard
import com.malbiseo.ui.common.ConfirmDialog
import com.malbiseo.ui.common.EmptyState
import com.malbiseo.ui.common.ScreenHeader
import com.malbiseo.ui.common.endMillis
import com.malbiseo.ui.common.formatAmPm
import com.malbiseo.ui.common.formatClock
import com.malbiseo.ui.common.formatDate
import com.malbiseo.ui.common.formatTime
import com.malbiseo.ui.common.startMillis
import com.malbiseo.ui.common.toLocalDate
import com.malbiseo.ui.theme.SaturdayColor
import com.malbiseo.ui.theme.SundayColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

private val remindOptions = listOf(-1 to "알림 없음", 0 to "정각", 5 to "5분 전", 10 to "10분 전", 30 to "30분 전", 60 to "1시간 전", 1440 to "하루 전")
private fun remindLabel(min: Int) = remindOptions.firstOrNull { it.first == min }?.second ?: "${min}분 전"

private fun YearMonth.key() = year * 12 + monthValue - 1
private fun monthOf(key: Int): YearMonth = YearMonth.of(key / 12, key % 12 + 1)

// ───────────────────────── 캘린더 + 선택한 날의 일정 ─────────────────────────

@Composable
fun ScheduleScreen(onOpen: (id: Long, epochDay: Long) -> Unit) {
    val context = LocalContext.current
    val dao = LocalUserData.current.schedules
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    var monthKey by rememberSaveable { mutableIntStateOf(YearMonth.from(today).key()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val month = monthOf(monthKey)
    val selected = LocalDate.ofEpochDay(selectedDay)

    val monthItems by remember(monthKey) {
        dao.between(month.atDay(1).startMillis(), month.atEndOfMonth().endMillis())
    }.collectAsState(initial = emptyList())
    val countByDay = remember(monthItems) { monthItems.groupingBy { it.startAt.toLocalDate() }.eachCount() }
    val dayItems = remember(monthItems, selectedDay) { monthItems.filter { it.startAt.toLocalDate() == selected } }

    fun moveMonth(delta: Int) {
        monthKey += delta
        val m = monthOf(monthKey)
        selectedDay = (if (m == YearMonth.from(today)) today else m.atDay(1)).toEpochDay()
    }
    fun jumpTo(date: LocalDate) {
        monthKey = YearMonth.from(date).key()
        selectedDay = date.toEpochDay()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("일정", subtitle = "${month.year}년") {
                if (selected != today) {
                    TextButton(onClick = { jumpTo(today) }) { Text("오늘") }
                }
            }

            AppCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                MonthCalendar(
                    month = month,
                    selected = selected,
                    today = today,
                    countByDay = countByDay,
                    onSelect = { selectedDay = it.toEpochDay() },
                    onMove = { moveMonth(it) },
                    onTitleClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> jumpTo(LocalDate.of(y, m + 1, d)) },
                            selected.year, selected.monthValue - 1, selected.dayOfMonth,
                        ).show()
                    },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selected == today) "오늘 · ${formatDate(selected)}" else formatDate(selected),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (dayItems.isNotEmpty()) {
                    Text(
                        "${dayItems.count { it.done }}/${dayItems.size} 완료",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (dayItems.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.EventAvailable,
                    title = "일정이 없어요",
                    subtitle = "＋ 버튼을 눌러 이 날의 일정을 추가하세요",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(dayItems, key = { it.id }) { s ->
                        ScheduleRow(
                            s,
                            onClick = { onOpen(s.id, selectedDay) },
                            onToggle = { checked ->
                                scope.launch {
                                    val updated = s.copy(done = checked)
                                    dao.update(updated)
                                    ReminderScheduler.schedule(context, updated)
                                }
                            },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onOpen(0L, selectedDay) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "일정 추가") }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    countByDay: Map<LocalDate, Int>,
    onSelect: (LocalDate) -> Unit,
    onMove: (Int) -> Unit,
    onTitleClick: () -> Unit,
) {
    val move by rememberUpdatedState(onMove)
    var drag by remember { mutableFloatStateOf(0f) }

    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            TextButton(onClick = onTitleClick, modifier = Modifier.weight(1f)) {
                Text(
                    "${month.monthValue}월",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onMove(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (i) {
                        0 -> SundayColor
                        6 -> SaturdayColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // 좌우로 밀어서 달 이동
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir } + fadeOut())
            },
            label = "month",
            modifier = Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 120f) move(-1) else if (drag < -120f) move(1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount -> drag += amount }
            },
        ) { m ->
            val offset = m.atDay(1).dayOfWeek.value % 7 // 일요일 시작
            val days = m.lengthOfMonth()
            Column {
                for (row in 0 until 6) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val day = row * 7 + col - offset + 1
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                                if (day in 1..days) {
                                    val date = m.atDay(day)
                                    DayCell(
                                        date = date,
                                        isSelected = date == selected,
                                        isToday = date == today,
                                        count = countByDay[date] ?: 0,
                                        onClick = { onSelect(date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isSelected: Boolean, isToday: Boolean, count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val dow = date.dayOfWeek.value % 7
    Column(
        Modifier
            .size(width = 42.dp, height = 44.dp)
            .clip(shape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> colors.onPrimary
                dow == 0 -> SundayColor
                dow == 6 -> SaturdayColor
                else -> colors.onSurface
            },
        )
        Row(
            Modifier.height(8.dp).padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(minOf(count, 3)) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (isSelected) colors.onPrimary else colors.tertiary)
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(s: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(54.dp)) {
                Text(formatAmPm(s.startAt), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(
                    formatClock(s.startAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (s.done) colors.onSurfaceVariant else colors.onSurface,
                )
            }
            Box(
                Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp))
                    .background(if (s.done) colors.outline else colors.primary)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (s.done) TextDecoration.LineThrough else null,
                    color = if (s.done) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    if (s.remindBeforeMin >= 0) "🔔 ${remindLabel(s.remindBeforeMin)}" else null,
                    s.note.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Checkbox(checked = s.done, onCheckedChange = onToggle)
        }
    }
}

// ───────────────────────── 추가 / 수정 ─────────────────────────

@Composable
fun ScheduleEditScreen(id: Long, initialEpochDay: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val dao = LocalUserData.current.schedules
    val scope = rememberCoroutineScope()
    val isNew = id == 0L

    var original by remember { mutableStateOf<Schedule?>(null) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var epochDay by rememberSaveable {
        mutableLongStateOf(if (initialEpochDay >= 0) initialEpochDay else LocalDate.now().toEpochDay())
    }
    var minuteOfDay by rememberSaveable {
        mutableIntStateOf(((LocalTime.now().hour + 1) % 24) * 60) // 기본: 다음 정시
    }
    var remind by rememberSaveable { mutableIntStateOf(10) }
    var remindMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (!isNew) {
            val s = dao.get(id)
            original = s
            if (!loaded && s != null) {
                val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.startAt), ZoneId.systemDefault())
                title = s.title
                note = s.note
                epochDay = s.startAt.toLocalDate().toEpochDay()
                minuteOfDay = dt.hour * 60 + dt.minute
                remind = s.remindBeforeMin
            }
        }
        loaded = true
    }

    val speech = rememberSpeechController { heard -> title = heard }
    val startMic = rememberWithMicPermission { speech.start() }

    val date = LocalDate.ofEpochDay(epochDay)
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(if (isNew) "일정 추가" else "일정 수정", onBack = onDone) {
            // 스크롤하지 않아도 보이게 위쪽에도 캘린더·공유 버튼
            val headerStart = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            IconButton(onClick = {
                if (title.isBlank()) error = "제목을 입력해 주세요."
                else addToPhoneCalendar(context, title.trim(), note.trim(), headerStart)
            }) { Icon(Icons.Filled.EditCalendar, contentDescription = "휴대폰 캘린더에 추가") }
            IconButton(onClick = {
                if (title.isBlank()) error = "제목을 입력해 주세요."
                else shareSchedule(context, title.trim(), note.trim(), headerStart)
            }) { Icon(Icons.Filled.Share, contentDescription = "공유") }
            if (!isNew) IconButton(onClick = { askDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "삭제")
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; error = null },
                label = { Text("일정 제목") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { if (speech.listening) speech.stop() else startMic() }) {
                        Icon(
                            if (speech.listening) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = "음성으로 입력",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (speech.listening) Text("듣는 중… ${speech.partial}", color = MaterialTheme.colorScheme.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    DatePickerDialog(
                        context,
                        { _, y, m, d -> epochDay = LocalDate.of(y, m + 1, d).toEpochDay() },
                        date.year, date.monthValue - 1, date.dayOfMonth,
                    ).show()
                }, modifier = Modifier.weight(1f)) { Text(formatDate(date)) }

                OutlinedButton(onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, min -> minuteOfDay = h * 60 + min },
                        time.hour, time.minute, false,
                    ).show()
                }, modifier = Modifier.weight(1f)) {
                    val ms = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    Text(formatTime(ms))
                }
            }

            Box {
                OutlinedButton(onClick = { remindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(remindLabel(remind))
                }
                DropdownMenu(expanded = remindMenu, onDismissRequest = { remindMenu = false }) {
                    remindOptions.forEach { (min, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { remind = min; remindMenu = false })
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("메모 (선택)") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )

            Button(
                onClick = {
                    val t = title.trim()
                    if (t.isEmpty()) {
                        error = "제목을 입력해 주세요."
                        return@Button
                    }
                    val startAt = LocalDateTime.of(date, time)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    scope.launch {
                        val o = original
                        val saved = if (o == null) {
                            val s = Schedule(title = t, note = note.trim(), startAt = startAt, remindBeforeMin = remind)
                            s.copy(id = dao.insert(s))
                        } else {
                            o.copy(title = t, note = note.trim(), startAt = startAt, remindBeforeMin = remind)
                                .also { dao.update(it) }
                        }
                        ReminderScheduler.schedule(context, saved)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("저장") }

            // 다른 캘린더·메신저로 보내기 (화면에 입력된 내용 기준)
            val currentStart = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (title.isBlank()) error = "제목을 입력해 주세요."
                        else addToPhoneCalendar(context, title.trim(), note.trim(), currentStart)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("휴대폰 캘린더에 추가")
                }
                OutlinedButton(
                    onClick = {
                        if (title.isBlank()) error = "제목을 입력해 주세요."
                        else shareSchedule(context, title.trim(), note.trim(), currentStart)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("공유")
                }
            }
            Text(
                "삼성 캘린더·구글 캘린더에 넣거나, 카카오톡·문자로 일정을 보낼 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (askDelete) {
        ConfirmDialog(
            title = "일정 삭제",
            text = "이 일정을 삭제할까요?",
            onConfirm = {
                askDelete = false
                val o = original
                scope.launch {
                    if (o != null) {
                        ReminderScheduler.cancel(context, o.id)
                        dao.delete(o)
                    }
                    onDone()
                }
            },
            onDismiss = { askDelete = false },
        )
    }
}
