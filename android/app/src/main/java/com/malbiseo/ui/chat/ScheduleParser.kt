package com.malbiseo.ui.chat

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * "내일 오후 3시 치과 일정 추가" 같은 한국어 문장에서 날짜·시간·제목을 뽑는다.
 * 외부 서비스 없이 기기 안에서 규칙으로 처리한다.
 */
object ScheduleParser {

    data class Result(
        val title: String?,
        val date: LocalDate?,
        val time: LocalTime?,
    ) {
        val hasWhen get() = date != null || time != null
    }

    // ───── 일정 추가 요청인지 ─────
    private val addWords = Regex("추가|등록|잡아|넣어|만들어|저장해|예약해|기록해")
    private val scheduleWords = Regex("일정|스케줄|약속|미팅|회의|모임|예약")
    private val questionWords = Regex("뭐|무슨|언제|있어|있나|알려|보여|확인|몇\\s*개")

    /** 일정 추가 요청이면 분석 결과, 아니면 null */
    fun parseAddRequest(text: String, now: LocalDateTime = LocalDateTime.now()): Result? {
        val t = text.trim()
        val r = parse(t, now)
        val explicitAdd = addWords.containsMatchIn(t) && (scheduleWords.containsMatchIn(t) || r.hasWhen)
        // "내일 오후 3시 치과"처럼 동사 없이 날짜+시간+내용만 말해도 일정으로 본다 (확인 질문으로 한 번 더 묻는다)
        val implicitAdd = r.date != null && r.time != null && r.title != null && !questionWords.containsMatchIn(t)
        return if (explicitAdd || implicitAdd) r else null
    }

    // ───── 분석 ─────
    private const val NUM = "(\\d{1,2}|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열한|열두|열)"
    private val koNum = mapOf(
        "한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7,
        "여덟" to 8, "아홉" to 9, "열" to 10, "열한" to 11, "열두" to 12,
    )
    private const val TAIL = "(?:\\s*(?:에|에는|부터|까지|쯤|경))?"

    private val reMonthDay = Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일$TAIL")
    private val reSlash = Regex("(?<![\\d:])(\\d{1,2})\\s*/\\s*(\\d{1,2})(?![\\d:])$TAIL")
    private val reRelDay = Regex("(내일\\s*모레|모레|글피|내일|오늘|금일)$TAIL")
    private val reAfter = Regex("(\\d{1,2})\\s*일\\s*(?:후|뒤)$TAIL")
    private val reWeekday = Regex("(다음\\s*주|담주|이번\\s*주|금주)?\\s*([월화수목금토일])요일$TAIL")
    private val reDayOnly = Regex("(?<![\\d월])(\\d{1,2})\\s*일(?!\\s*(?:후|뒤|정|간))$TAIL")
    private val reNextWeek = Regex("(다음\\s*주|담주)$TAIL")

    private val reClock = Regex("(오전|오후|아침|점심|낮|저녁|밤|새벽)?\\s*(\\d{1,2}):(\\d{2})$TAIL")
    private val reHour = Regex("(오전|오후|아침|점심|낮|저녁|밤|새벽)?\\s*$NUM\\s*시(?!간)\\s*(?:(반)|(\\d{1,2})\\s*분)?$TAIL")
    private val reNoon = Regex("(정오|점심\\s*때)$TAIL")
    // 시각 없이 "저녁에", "아침쯤"처럼 때만 말한 경우 (뒤에 에/쯤/때가 붙을 때만: "점심 약속"의 점심은 제목으로 남김)
    private val reVague = Regex("(아침|오전|점심|오후|저녁|밤|새벽)\\s*(?:에|쯤|때|경)(?=\\s)")
    private val vagueTimes = mapOf(
        "새벽" to LocalTime.of(6, 0), "아침" to LocalTime.of(8, 0), "오전" to LocalTime.of(10, 0),
        "점심" to LocalTime.NOON, "오후" to LocalTime.of(15, 0), "저녁" to LocalTime.of(19, 0), "밤" to LocalTime.of(21, 0),
    )

    private val weekdays = mapOf(
        '월' to DayOfWeek.MONDAY, '화' to DayOfWeek.TUESDAY, '수' to DayOfWeek.WEDNESDAY,
        '목' to DayOfWeek.THURSDAY, '금' to DayOfWeek.FRIDAY, '토' to DayOfWeek.SATURDAY, '일' to DayOfWeek.SUNDAY,
    )

    fun parse(text: String, now: LocalDateTime = LocalDateTime.now()): Result {
        var rest = " " + text.replace(Regex("\\s+"), " ").trim() + " "
        val today = now.toLocalDate()

        fun take(re: Regex): MatchResult? {
            val m = re.find(rest) ?: return null
            rest = rest.removeRange(m.range).let { it.substring(0, m.range.first) + " " + it.substring(m.range.first) }
            return m
        }

        // ── 시간 (날짜보다 먼저: "3시"의 숫자를 날짜로 오인하지 않게) ──
        var time: LocalTime? = null
        var weekdayLoose = false
        take(reClock)?.let { m ->
            time = toTime(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt())
        } ?: take(reHour)?.let { m ->
            val h = m.groupValues[2].let { it.toIntOrNull() ?: koNum[it] ?: 0 }
            val min = when {
                m.groupValues[3].isNotEmpty() -> 30
                m.groupValues[4].isNotEmpty() -> m.groupValues[4].toInt()
                else -> 0
            }
            time = toTime(m.groupValues[1], h, min)
        } ?: take(reNoon)?.let { time = LocalTime.NOON }
            ?: take(reVague)?.let { m -> time = vagueTimes[m.groupValues[1]] }

        // ── 날짜 ──
        var date: LocalDate? = null
        take(reMonthDay)?.let { m ->
            date = safeDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                ?.let { if (it.isBefore(today)) it.plusYears(1) else it }
        }
        if (date == null) take(reSlash)?.let { m ->
            date = safeDate(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt())
                ?.let { if (it.isBefore(today)) it.plusYears(1) else it }
        }
        if (date == null) take(reAfter)?.let { m -> date = today.plusDays(m.groupValues[1].toLong()) }
        if (date == null) take(reRelDay)?.let { m ->
            date = when (m.groupValues[1].replace(" ", "")) {
                "오늘", "금일" -> today
                "내일" -> today.plusDays(1)
                "모레", "내일모레" -> today.plusDays(2)
                "글피" -> today.plusDays(3)
                else -> null
            }
        }
        if (date == null) take(reWeekday)?.let { m ->
            val dow = weekdays.getValue(m.groupValues[2][0])
            val qualifier = m.groupValues[1].replace(" ", "")
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            date = when (qualifier) {
                "다음주", "담주" -> monday.plusWeeks(1).with(TemporalAdjusters.nextOrSame(dow))
                "이번주", "금주" -> monday.with(TemporalAdjusters.nextOrSame(dow))
                else -> { weekdayLoose = true; today.with(TemporalAdjusters.nextOrSame(dow)) }
            }
        }
        if (date == null) take(reDayOnly)?.let { m ->
            val d = m.groupValues[1].toInt()
            date = safeDate(today.year, today.monthValue, d)?.let { if (it.isBefore(today)) null else it }
                ?: today.plusMonths(1).let { safeDate(it.year, it.monthValue, d) }
        }
        if (date == null) take(reNextWeek)?.let { date = today.plusWeeks(1) }

        // 요일만 말했는데 오늘 그 시간이 이미 지났으면 다음 주로
        if (weekdayLoose && date == today && time != null && !time!!.isAfter(now.toLocalTime())) {
            date = date!!.plusWeeks(1)
        }

        return Result(title = cleanTitle(rest), date = date, time = time)
    }

    private fun toTime(period: String, hour: Int, minute: Int): LocalTime? {
        if (hour !in 0..24 || minute !in 0..59) return null
        var h = hour % 24
        when (period) {
            "오후", "저녁", "밤" -> if (h in 1..11) h += 12 else if (h == 12 && period == "밤") h = 0
            "점심", "낮" -> if (h in 1..5) h += 12
            "오전", "아침", "새벽" -> if (h == 12) h = 0
            else -> if (h in 1..6) h += 12 // "3시"는 보통 오후 3시
        }
        return LocalTime.of(h, minute)
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    // ───── 제목 정리 ─────
    private val removeWords = listOf(
        "(?:일정|스케줄)\\s*(?:을|를|으로|로|에|은|는)?",
        "(?:추가|등록|저장|기록)\\s*(?:해\\s*줘|해\\s*주세요|해\\s*줄래|해\\s*요|해|하기|할래|하자|좀)?",
        "(?:잡아|넣어|만들어|예약해)\\s*(?:줘|주세요|줄래|요)?",
        "부탁해(?:요)?", "부탁드려요", "말비서야?", "좀", "해\\s*줘",
    ).map { Regex("(?<=^|\\s)$it(?=\\s|$|[.!?~])") }

    private fun cleanTitle(raw: String): String? {
        var s = raw
        removeWords.forEach { s = it.replace(s, " ") }
        s = s.replace(Regex("[.!?~,]+"), " ").replace(Regex("\\s+"), " ").trim()
        // 앞뒤에 남은 조사 정리 ("에 치과" → "치과")
        val particles = setOf("에", "에는", "은", "는", "을", "를", "로", "으로", "부터", "까지", "쯤", "경", "때")
        val words = s.split(" ").toMutableList()
        while (words.isNotEmpty() && words.first() in particles) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in particles) words.removeAt(words.lastIndex)
        return words.joinToString(" ").trim().takeIf { it.isNotEmpty() }
    }
}
