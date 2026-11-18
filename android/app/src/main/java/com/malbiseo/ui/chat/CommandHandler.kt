package com.malbiseo.ui.chat

import com.malbiseo.data.UserData
import com.malbiseo.data.Memo
import com.malbiseo.ui.Routes
import com.malbiseo.ui.common.endMillis
import com.malbiseo.ui.common.formatTime
import com.malbiseo.ui.common.startMillis
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatMessage(val text: String, val fromUser: Boolean)

sealed interface CommandResult {
    data class Reply(val text: String) : CommandResult
    data class Navigate(val route: String, val text: String) : CommandResult
}

/** 기존 Flask handle_command() 를 옮기고 확장한 것 */
class CommandHandler(private val data: UserData) {

    suspend fun handle(input: String): CommandResult {
        val cmd = input.trim()

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

    companion object {
        const val HELP = "아직 이해하지 못했어요. 이렇게 말해 보세요:\n" +
            "• \"메모 내일 우유 사기\"\n" +
            "• \"회의 시작\"\n" +
            "• \"오늘 일정\" / \"내일 일정\"\n" +
            "• \"지금 몇 시야\""
    }
}
