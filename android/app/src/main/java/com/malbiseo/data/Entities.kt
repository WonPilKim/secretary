package com.malbiseo.data

/*
 * 앱에서 쓰는 데이터 모델.
 * 저장은 Firestore 의 users/{uid}/memos|meetings|schedules/{id} 문서로 한다 (Repositories 참고).
 * id 는 기기에서 만드는 Long 값이고 문서 id 로 그대로 쓴다.
 */

/** 로그인한 사용자. id 는 Firebase Auth uid */
data class User(
    val id: String,
    val name: String,
    val loginId: String,
)

data class Memo(
    val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long? = null,
)

data class Meeting(
    val id: Long = 0,
    val title: String,
    val transcript: String,
    val summary: String? = null,
    val durationSec: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Schedule(
    val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 시작 시각 (epoch millis) */
    val startAt: Long,
    /** 몇 분 전에 알릴지. -1 이면 알림 없음 */
    val remindBeforeMin: Int = 10,
    val done: Boolean = false,
)
