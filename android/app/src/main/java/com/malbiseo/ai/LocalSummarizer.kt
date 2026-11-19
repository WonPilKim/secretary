package com.malbiseo.ai

import kotlin.math.sqrt

/**
 * AI 없이 기기 안에서 회의록을 정리하는 무료 요약.
 *
 * - 자주 나온 단어(키워드)를 많이 담은 문장을 핵심 문장으로 고른다.
 * - "하기로/결정", "까지/담당", "다음 주/내일" 같은 표현으로 결정·할 일·일정을 뽑는다.
 *
 * 출력 형식은 Claude 요약과 같은 ■ 섹션 구조를 따른다.
 */
object LocalSummarizer {

    private data class Sentence(val index: Int, val time: String?, val text: String, val tokens: List<String>)

    private val timeRegex = Regex("^\\[([^\\]]*)\\]\\s*")

    private val particles = listOf(
        "에서부터", "으로부터", "이었습니다", "였습니다", "입니다", "습니다", "에서는", "에게서", "한테서",
        "으로는", "이라는", "라는", "에서", "에게", "한테", "께서", "으로", "부터", "까지", "처럼", "보다",
        "이랑", "하고", "이나", "이다", "은", "는", "이", "가", "을", "를", "에", "로", "와", "과", "도",
        "의", "만", "랑", "나", "요",
    )

    private val stopwords = setOf(
        "그", "저", "이거", "그거", "저거", "이것", "그것", "근데", "그런데", "그래서", "그리고", "그러면",
        "그럼", "음", "어", "아", "네", "예", "응", "좀", "진짜", "정말", "그냥", "이제", "뭐", "약간",
        "일단", "우리", "저희", "제가", "내가", "저는", "나는", "하는", "있는", "없는", "되는", "것", "거",
        "수", "때", "등", "및", "또", "더", "잘", "안", "못", "다", "이런", "그런", "저런", "어떤", "같은",
        "같아요", "합니다", "했습니다", "하고", "해서", "하면", "있습니다", "있어요", "없어요", "그렇게",
        "이렇게", "많이", "지금", "오늘", "혹시", "아마", "다시", "한번", "그거는", "이거는",
    )

    private val decisionRegex = Regex("결정|확정|하기로|되기로|합의|정했|정하자|정하죠|가기로|진행하기로|승인|채택|최종")
    private val todoRegex = Regex(
        "해야|해\\s?주세요|해\\s?줘|부탁|담당|맡아|맡기|챙겨|까지\\s?(?:정리|준비|공유|전달|보내|제출|작성|확인|완료|해|하)|" +
            "(?:준비|공유|전달|보내|정리|작성|확인|검토|연락|제출|조사|수정)(?:하겠|할게|할께|해\\s?드리|해\\s?놓|해\\s?두)"
    )
    private val scheduleRegex = Regex(
        "다음\\s?주|다음\\s?달|다음\\s?회의|내일|모레|월요일|화요일|수요일|목요일|금요일|토요일|일요일|" +
            "\\d+\\s?월\\s?\\d+\\s?일|\\d+\\s?일|\\d+\\s?시|오전|오후|주말|이번\\s?주"
    )

    fun summarize(title: String, transcript: String): String {
        val sentences = parse(transcript)
        if (sentences.isEmpty()) throw IllegalStateException("요약할 녹취 내용이 없어요.")

        val freq = HashMap<String, Int>()
        sentences.forEach { s -> s.tokens.toSet().forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        // 제목에 나온 단어는 가중치
        tokenize(title).forEach { freq[it] = (freq[it] ?: 0) + 2 }

        val keywords = freq.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .take(6)
            .map { it.key }

        fun score(s: Sentence): Double {
            if (s.tokens.size < 2) return 0.0
            val sum = s.tokens.sumOf { (freq[it] ?: 1) - 1 }.toDouble()
            val bonus = if (decisionRegex.containsMatchIn(s.text) || todoRegex.containsMatchIn(s.text)) 1.5 else 0.0
            return sum / sqrt(s.tokens.size.toDouble()) + bonus
        }

        val scored = sentences.map { it to score(it) }
        val best = scored.maxByOrNull { it.second }?.first ?: sentences.first()

        val pointCount = (sentences.size / 4).coerceIn(3, 7)
        val points = scored
            .filter { it.second > 0 || sentences.size <= pointCount }
            .sortedByDescending { it.second }
            .take(pointCount)
            .map { it.first }
            .distinctBy { normalize(it.text) }
            .sortedBy { it.index }
            .ifEmpty { sentences.take(pointCount) }

        val decisions = sentences.filter { decisionRegex.containsMatchIn(it.text) }.dedupe().take(5)
        val todos = sentences.filter { todoRegex.containsMatchIn(it.text) && it !in decisions }.dedupe().take(5)
        val schedules = sentences
            .filter { scheduleRegex.containsMatchIn(it.text) && it !in todos }
            .dedupe().take(3)

        return buildString {
            appendLine("■ 한 줄 요약")
            appendLine(shorten(best.text, 80))
            if (keywords.isNotEmpty()) appendLine("핵심 단어: " + keywords.joinToString(", "))
            appendLine()
            appendLine("■ 주요 논의 내용")
            points.forEach { appendLine(bullet(it)) }
            appendLine()
            appendLine("■ 결정 사항")
            if (decisions.isEmpty()) appendLine("없음") else decisions.forEach { appendLine(bullet(it)) }
            appendLine()
            appendLine("■ 할 일")
            if (todos.isEmpty()) appendLine("없음") else todos.forEach { appendLine(bullet(it)) }
            appendLine()
            appendLine("■ 다음 일정")
            if (schedules.isEmpty()) appendLine("없음") else schedules.forEach { appendLine(bullet(it)) }
            appendLine()
            append("※ 무료 요약: AI 없이 녹취록에서 핵심 문장을 골라 정리했어요. 표현은 녹취록 그대로예요.")
        }
    }

    private fun List<Sentence>.dedupe() = distinctBy { normalize(it.text) }

    private fun bullet(s: Sentence) = "• " + shorten(s.text, 120) + (s.time?.let { " ($it)" } ?: "")

    private fun normalize(text: String) = text.replace(Regex("\\s+"), "")

    private fun shorten(text: String, max: Int) =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    private fun parse(transcript: String): List<Sentence> {
        val result = mutableListOf<Sentence>()
        transcript.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val time = timeRegex.find(line)?.groupValues?.get(1)
            val body = line.replace(timeRegex, "").trim()
            // 문장부호나 한국어 종결 표현 뒤에서 나눈다
            body.split(Regex("(?<=[.?!。])\\s+|(?<=(?:니다|어요|아요|해요|세요|죠|다고|네요|거든요))\\s+(?=\\S{2,})"))
                .map { it.trim() }
                .filter { it.length >= 4 }
                .forEach { text -> result += Sentence(result.size, time, text, tokenize(text)) }
        }
        return result
    }

    private fun tokenize(text: String): List<String> =
        text.split(Regex("[^0-9A-Za-z가-힣]+"))
            .asSequence()
            .map { stripParticle(it.lowercase()) }
            .filter { it.length >= 2 && it !in stopwords && !it.all(Char::isDigit) }
            .toList()

    private fun stripParticle(word: String): String {
        if (word.length < 3) return word
        for (p in particles) {
            if (word.endsWith(p) && word.length - p.length >= 2) return word.dropLast(p.length)
        }
        return word
    }
}
