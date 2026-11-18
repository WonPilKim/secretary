package com.malbiseo.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Claude Messages API로 회의록을 요약한다. */
class ClaudeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun summarizeMeeting(apiKey: String, model: String, title: String, transcript: String): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw IllegalStateException("설정 탭에서 Claude API 키를 먼저 입력해 주세요.")
            if (transcript.isBlank()) throw IllegalStateException("요약할 녹취 내용이 없어요.")

            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 2048)
                .put("system", SYSTEM_PROMPT)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "회의 제목: $title\n\n<녹취록>\n$transcript\n</녹취록>")
                    )
                )

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(text).getJSONObject("error").getString("message")
                    }.getOrDefault(text.take(200))
                    throw IOException("요약 실패 (${res.code}): $msg")
                }
                val content = JSONObject(text).getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.getString("text"))
                    }
                }.trim()
            }
        }

    companion object {
        private val SYSTEM_PROMPT = """
            당신은 한국어 회의록 정리 비서입니다. 음성 인식으로 받아쓴 녹취록이라 오타나 끊긴 문장이 있을 수 있으니 문맥으로 보정해서 이해하세요.
            아래 형식으로 간결하게 정리하세요. 녹취록에 없는 내용은 지어내지 마세요. 해당 내용이 없으면 "없음"이라고 쓰세요.

            ■ 한 줄 요약
            ■ 주요 논의 내용 (글머리표 3~7개)
            ■ 결정 사항
            ■ 할 일 (담당자 / 기한이 언급되었다면 함께)
            ■ 다음 일정
        """.trimIndent()
    }
}
