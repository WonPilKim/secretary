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

private val dayFmt = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)
private val timeOnlyFmt = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREA)

/** 여러 일정을 한 메시지로 공유 (예: 그날 일정 전체) */
fun shareSchedules(context: Context, day: java.time.LocalDate, items: List<com.malbiseo.data.Schedule>) {
    if (items.isEmpty()) return
    val zone = ZoneId.systemDefault()
    val text = buildString {
        appendLine("[일정] ${dayFmt.format(day)}")
        items.sortedBy { it.startAt }.forEach { s ->
            append("• ${timeOnlyFmt.format(Instant.ofEpochMilli(s.startAt).atZone(zone))} ${s.title}")
            if (s.done) append(" (완료)")
            appendLine()
            if (s.note.isNotBlank()) appendLine("   ${s.note.trim().replace("\n", " ")}")
        }
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

/**
 * 여러 일정을 표준 캘린더 파일(.ics)로 만들어 휴대폰 캘린더 앱의 "가져오기"로 넘긴다.
 * 삼성 캘린더는 .ics 를 열어 한꺼번에 가져올 수 있다. 열 수 있는 앱이 없으면 파일 공유 화면을 띄운다.
 */
fun exportToPhoneCalendar(context: Context, fileTag: String, items: List<com.malbiseo.data.Schedule>, durationMin: Int = 60) {
    if (items.isEmpty()) return
    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
    val file = java.io.File(dir, "malbiseo_$fileTag.ics")
    file.writeText(IcsWriter.build(items, durationMin), Charsets.UTF_8)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/calendar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).setType("text/calendar")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(send, "캘린더 파일 보내기"))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "캘린더 파일을 열 수 있는 앱이 없어요.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** iCalendar(.ics, RFC 5545) 작성기 */
object IcsWriter {
    private val utcFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)

    fun build(items: List<com.malbiseo.data.Schedule>, durationMin: Int = 60, now: Long = System.currentTimeMillis()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Malbiseo//Malbiseo App//KO",
            "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
        )
        items.sortedBy { it.startAt }.forEach { s ->
            lines += "BEGIN:VEVENT"
            lines += "UID:${s.id}@malbiseo.kr"
            lines += "DTSTAMP:${utcFmt.format(Instant.ofEpochMilli(now))}"
            lines += "DTSTART:${utcFmt.format(Instant.ofEpochMilli(s.startAt))}"
            lines += "DTEND:${utcFmt.format(Instant.ofEpochMilli(s.startAt + durationMin * 60_000L))}"
            lines += "SUMMARY:${escape(s.title)}"
            if (s.note.isNotBlank()) lines += "DESCRIPTION:${escape(s.note.trim())}"
            if (s.remindBeforeMin >= 0) {
                lines += "BEGIN:VALARM"
                lines += "ACTION:DISPLAY"
                lines += "DESCRIPTION:${escape(s.title)}"
                lines += "TRIGGER:-PT${s.remindBeforeMin}M"
                lines += "END:VALARM"
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n")

    /** 한 줄이 75바이트를 넘으면 접는다 (한글 글자가 쪼개지지 않게) */
    private fun fold(line: String): String {
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > 74) { out.append("\r\n "); bytes = 1 }
            out.appendCodePoint(cp)
            bytes += len
            i += Character.charCount(cp)
        }
        return out.toString()
    }
}
