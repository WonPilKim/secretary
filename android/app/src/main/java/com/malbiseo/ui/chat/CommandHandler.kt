package com.malbiseo.ui.chat

import android.content.Context
import com.malbiseo.data.UserData
import com.malbiseo.data.Memo
import com.malbiseo.data.Schedule
import com.malbiseo.reminder.ReminderScheduler
import com.malbiseo.ui.Routes
import com.malbiseo.ui.common.endMillis
import com.malbiseo.ui.common.formatTime
import com.malbiseo.ui.common.startMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatMessage(val text: String, val fromUser: Boolean)

sealed interface CommandResult {
    data class Reply(val text: String) : CommandResult
    data class Navigate(val route: String, val text: String) : CommandResult
}

/** 기존 Flask handle_command() 를 옮기고 확장한 것 */
class CommandHandler(private val data: UserData, context: Context) {
    private val appContext = context.applicationContext

    /** 일정 추가 대화 상태: 제목을 묻는 중이거나, 저장할지 확인하는 중 */
    private var askingTitleFor: LocalDateTime? = null
    private var pendingSchedule: Schedule? = null

    suspend fun handle(input: String): CommandResult {
        val cmd = input.trim()

        // ── 일정 추가 대화 이어가기 ──
        askingTitleFor?.let { at ->
            askingTitleFor = null
            if (NO.containsMatchIn(cmd)) return CommandResult.Reply("일정 추가를 취소했어요.")
            val title = cmd.trim().trimEnd('.', '!', '?').ifEmpty { "일정" }
            return confirm(Schedule(title = title, startAt = at.toMillis()), timeGuessed = false)
        }
        pendingSchedule?.let { p ->
            pendingSchedule = null
            when {
                YES.containsMatchIn(cmd) && cmd.length <= 15 -> return save(p)
                NO.containsMatchIn(cmd) && cmd.length <= 15 -> return CommandResult.Reply("일정 추가를 취소했어요.")
                // 그 밖의 말은 새 명령으로 처리
            }
        }

        // "메모" → 메모 화면, "메모 우유 사기" / "메모해줘 ..." → 바로 저장
        if (cmd.startsWith("메모")) {
            val content = cmd.removePrefix("메모")
                .trimStart(' ', ':', ',')
                .removePrefix("해줘").removePrefix("해 줘")
                .trim()
            return if (content.isEmpty()) {
                CommandResult.Navigate(Routes.MEMOS, "메모 화면으로 이동할게요.")
            } else {
                data.memos.insert(Memo(text = content))
                CommandResult.Reply("메모했어요: \"$content\"")
            }
        }

        // ── 일정 추가: "내일 오후 3시 치과 일정 추가" (메모 명령 다음, "회의 일정 추가"를 위해 회의보다 먼저) ──
        ScheduleParser.parseAddRequest(cmd)?.let { r -> return startAdd(r) }

        if ("회의" in cmd) {
            return if ("시작" in cmd || "녹음" in cmd || "기록" in cmd) {
                CommandResult.Navigate(Routes.MEETING_RECORD, "회의 기록을 시작할게요.")
            } else {
                CommandResult.Navigate(Routes.MEETINGS, "회의록 목록으로 이동할게요.")
            }
        }

        if ("일정" in cmd || "스케줄" in cmd) {
            val day = when {
                "오늘" in cmd -> LocalDate.now()
                "내일" in cmd -> LocalDate.now().plusDays(1)
                else -> null
            } ?: return CommandResult.Navigate(Routes.SCHEDULE, "일정 화면으로 이동할게요.")

            val label = if (day == LocalDate.now()) "오늘" else "내일"
            val list = data.schedules.listBetween(day.startMillis(), day.endMillis())
            return CommandResult.Reply(
                if (list.isEmpty()) "$label 일정은 없어요."
                else "$label 일정 ${list.size}개:\n" + list.joinToString("\n") {
                    "• ${formatTime(it.startAt)} ${it.title}" + if (it.done) " (완료)" else ""
                }
            )
        }

        if ("안녕" in cmd) return CommandResult.Reply("안녕하세요! 무엇을 도와드릴까요?")

        if ("시간" in cmd || "몇 시" in cmd) {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("a h시 m분", Locale.KOREA))
            return CommandResult.Reply("현재 시간은 $now 입니다.")
        }

        if ("날짜" in cmd || "며칠" in cmd || "무슨 요일" in cmd) {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREA))
            return CommandResult.Reply("오늘은 $today 입니다.")
        }

        return CommandResult.Reply(HELP)
    }

    private fun startAdd(r: ScheduleParser.Result): CommandResult {
        val now = LocalDateTime.now()
        if (!r.hasWhen && r.title == null) {
            return CommandResult.Reply(
                "어떤 일정을 언제로 추가할까요?\n예: \"내일 오후 3시 치과 일정 추가\", \"금요일 10시 반 주간 회의 잡아줘\""
            )
        }
        if (!r.hasWhen) {
            return CommandResult.Reply("\"${r.title}\" 일정을 언제로 할까요?\n예: \"내일 오후 3시 ${r.title} 일정 추가\"")
        }
        // 날짜만 있으면 오전 9시, 시간만 있으면 오늘(이미 지났으면 내일)
        val timeGuessed = r.time == null
        val time = r.time ?: LocalTime.of(9, 0)
        var date = r.date ?: now.toLocalDate()
        if (r.date == null && !LocalDateTime.of(date, time).isAfter(now)) date = date.plusDays(1)
        val at = LocalDateTime.of(date, time)

        if (r.title == null) {
            askingTitleFor = at
            return CommandResult.Reply("${describe(at)}에 무슨 일정인가요?")
        }
        return confirm(Schedule(title = r.title, startAt = at.toMillis()), timeGuessed)
    }

    private fun confirm(s: Schedule, timeGuessed: Boolean): CommandResult {
        pendingSchedule = s
        val guess = if (timeGuessed) "\n(시간을 말씀하지 않아 오전 9시로 정했어요)" else ""
        val past = if (s.startAt <= System.currentTimeMillis()) "\n(이미 지난 시간이에요)" else ""
        return CommandResult.Reply("${describe(s.startAt.toLocalDateTime())}, \"${s.title}\"$guess$past\n이대로 일정에 추가할까요? \"네\" 또는 \"아니요\"로 답해 주세요.")
    }

    private suspend fun save(p: Schedule): CommandResult {
        val saved = p.copy(id = data.schedules.insert(p))
        ReminderScheduler.schedule(appContext, saved)
        val remind = if (saved.startAt - saved.remindBeforeMin * 60_000L > System.currentTimeMillis())
            " ${saved.remindBeforeMin}분 전에 알려 드릴게요." else ""
        return CommandResult.Reply(
            "일정에 추가했어요.$remind\n일정 화면에서 휴대폰 캘린더에 넣거나 카카오톡으로 공유할 수 있어요."
        )
    }

    private fun describe(at: LocalDateTime): String = at.format(DESCRIBE_FMT)
    private fun LocalDateTime.toMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun Long.toLocalDateTime() =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), ZoneId.systemDefault())

    companion object {
        private val YES = Regex("^\\s*(네|넵|예|응|어|그래|좋아|맞아|오케이|ok|okay|저장|추가|확인|ㅇㅇ|ㅇㅋ)(?=$|\\s|[.!~,]|요|해|야|지)", RegexOption.IGNORE_CASE)
        private val NO = Regex("^\\s*(아니|아뇨|노|취소|됐어|안\\s*해|하지\\s*마|ㄴㄴ)(?=$|\\s|[.!~,]|요|야|오|해)")
        private val DESCRIBE_FMT = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREA)

        const val HELP = "아직 이해하지 못했어요. 이렇게 말해 보세요:\n" +
            "• \"메모 내일 우유 사기\"\n" +
            "• \"내일 오후 3시 치과 일정 추가\"\n" +
            "• \"회의 시작\"\n" +
            "• \"오늘 일정\" / \"내일 일정\"\n" +
            "• \"지금 몇 시야\""
    }
}
