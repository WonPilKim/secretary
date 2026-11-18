package com.malbiseo.data

import com.google.firebase.firestore.FirebaseFirestore

/**
 * 로그인한 사용자 한 명의 클라우드 데이터 묶음 (예전 Room AppDatabase 자리).
 *
 * Firestore 구조
 *   users/{uid}                       ← 이름, 아이디, 가입일
 *   users/{uid}/memos/{id}
 *   users/{uid}/meetings/{id}
 *   users/{uid}/schedules/{id}
 */
class UserData(val uid: String) {
    private val root = FirebaseFirestore.getInstance().collection("users").document(uid)

    val memos = MemoRepository(root.collection("memos"))
    val meetings = MeetingRepository(root.collection("meetings"))
    val schedules = ScheduleRepository(root.collection("schedules"))
}
