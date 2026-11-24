package com.malbiseo.ui.schedule

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shareFmt = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREA)

/**
 * 휴대폰 캘린더 앱(삼성 캘린더, 구글 캘린더 등)의 "일정 추가" 화면을 내용이 채워진 채로 연다.
 * 사용자가 그 앱에서 저장을 눌러야 들어가므로 캘린더 권한이 필요 없다.
 */
fun addToPhoneCalendar(context: Context, title: String, note: String, startAt: Long, durationMin: Int = 60) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startAt)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startAt + durationMin * 60_000L)
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, note)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "일정을 추가할 수 있는 캘린더 앱이 없어요.", Toast.LENGTH_SHORT).show()
    }
}

/** 일정을 글로 정리해 공유 (카카오톡, 문자, 메일 등 사용자가 고름) */
fun shareSchedule(context: Context, title: String, note: String, startAt: Long) {
    val whenText = shareFmt.format(Instant.ofEpochMilli(startAt).atZone(ZoneId.systemDefault()))
    val text = buildString {
        appendLine("[일정] $title")
        appendLine("일시: $whenText")
        if (note.isNotBlank()) appendLine("메모: ${note.trim()}")
        appendLine()
        append("말비서에서 보냄 · malbiseo.kr")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, "일정 공유"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "공유할 수 있는 앱이 없어요.", Toast.LENGTH_SHORT).show()
    }
}
