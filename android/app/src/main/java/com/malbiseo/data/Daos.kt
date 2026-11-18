package com.malbiseo.data

// Firestore 저장소 (예전 Room DAO 자리). 각 저장소는 로그인한 사용자 한 명의 데이터만 다룬다.
//
// - 쓰기(insert/update/delete)는 기다리지 않는다: Firestore 가 로컬 캐시에 먼저 반영하고
//   목록 화면이 즉시 갱신되며, 인터넷이 연결되면 서버로 올라간다. (오프라인에서도 동작)
// - 읽기는 실시간 리스너(Flow)로 받아서 다른 폰에서 바꾼 내용도 바로 반영된다.

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

private const val TAG = "MalbiseoData"

/** 시간 기반 고유 id (다른 폰과 겹치지 않도록 난수 섞음) */
fun newId(): Long = System.currentTimeMillis() * 1000 + Random.nextInt(1000)

private fun Task<*>.logFailure(what: String) = addOnFailureListener { Log.w(TAG, "$what 실패", it) }

private fun <T> Query.listFlow(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) {
            Log.w(TAG, "목록 불러오기 실패", err)
            trySend(emptyList())
        } else {
            trySend(snap?.documents?.mapNotNull(map) ?: emptyList())
        }
    }
    awaitClose { reg.remove() }
}

private fun <T> DocumentReference.docFlow(map: (DocumentSnapshot) -> T?): Flow<T?> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) {
            Log.w(TAG, "문서 불러오기 실패", err)
            trySend(null)
        } else {
            trySend(snap?.let(map))
        }
    }
    awaitClose { reg.remove() }
}

private fun DocumentSnapshot.longId(): Long? = if (exists()) id.toLongOrNull() else null

// ───────── 메모 ─────────
class MemoRepository(private val col: CollectionReference) {
    private fun from(d: DocumentSnapshot): Memo? {
        val id = d.longId() ?: return null
        return Memo(
            id = id,
            text = d.getString("text").orEmpty(),
            createdAt = d.getLong("createdAt") ?: 0L,
            updatedAt = d.getLong("updatedAt"),
        )
    }
    private fun Memo.toMap() = mapOf("text" to text, "createdAt" to createdAt, "updatedAt" to updatedAt)

    /** 빈 문자열이면 전체, 아니면 내용 검색 */
    fun search(q: String): Flow<List<Memo>> =
        col.orderBy("createdAt", Query.Direction.DESCENDING).listFlow(::from)
            .map { list -> if (q.isBlank()) list else list.filter { it.text.contains(q, ignoreCase = true) } }

    suspend fun get(id: Long): Memo? = from(col.document(id.toString()).get().await())

    fun insert(memo: Memo): Long {
        val id = if (memo.id == 0L) newId() else memo.id
        col.document(id.toString()).set(memo.toMap()).logFailure("메모 저장")
        return id
    }
    fun update(memo: Memo) { col.document(memo.id.toString()).set(memo.toMap()).logFailure("메모 수정") }
    fun delete(memo: Memo) { col.document(memo.id.toString()).delete().logFailure("메모 삭제") }
}

// ───────── 회의록 ─────────
class MeetingRepository(private val col: CollectionReference) {
    private fun from(d: DocumentSnapshot): Meeting? {
        val id = d.longId() ?: return null
        return Meeting(
            id = id,
            title = d.getString("title").orEmpty(),
            transcript = d.getString("transcript").orEmpty(),
            summary = d.getString("summary"),
            durationSec = (d.getLong("durationSec") ?: 0L).toInt(),
            createdAt = d.getLong("createdAt") ?: 0L,
        )
    }
    private fun Meeting.toMap() = mapOf(
        "title" to title, "transcript" to transcript, "summary" to summary,
        "durationSec" to durationSec, "createdAt" to createdAt,
    )

    fun all(): Flow<List<Meeting>> = col.orderBy("createdAt", Query.Direction.DESCENDING).listFlow(::from)
    fun observe(id: Long): Flow<Meeting?> = col.document(id.toString()).docFlow(::from)

    fun insert(meeting: Meeting): Long {
        val id = if (meeting.id == 0L) newId() else meeting.id
        col.document(id.toString()).set(meeting.toMap()).logFailure("회의록 저장")
        return id
    }
    fun update(meeting: Meeting) { col.document(meeting.id.toString()).set(meeting.toMap()).logFailure("회의록 수정") }
    fun delete(meeting: Meeting) { col.document(meeting.id.toString()).delete().logFailure("회의록 삭제") }
}

// ───────── 일정 ─────────
class ScheduleRepository(private val col: CollectionReference) {
    private fun from(d: DocumentSnapshot): Schedule? {
        val id = d.longId() ?: return null
        return Schedule(
            id = id,
            title = d.getString("title").orEmpty(),
            note = d.getString("note").orEmpty(),
            startAt = d.getLong("startAt") ?: 0L,
            remindBeforeMin = (d.getLong("remindBeforeMin") ?: 10L).toInt(),
            done = d.getBoolean("done") ?: false,
        )
    }
    private fun Schedule.toMap() = mapOf(
        "title" to title, "note" to note, "startAt" to startAt,
        "remindBeforeMin" to remindBeforeMin, "done" to done,
    )
    private fun range(from: Long, to: Long) =
        col.whereGreaterThanOrEqualTo("startAt", from).whereLessThan("startAt", to).orderBy("startAt")

    fun between(from: Long, to: Long): Flow<List<Schedule>> = range(from, to).listFlow(::from)
    suspend fun listBetween(from: Long, to: Long): List<Schedule> =
        range(from, to).get().await().documents.mapNotNull(::from)

    /** 알림 재예약용: 앞으로 남은 미완료 일정 */
    suspend fun upcoming(now: Long): List<Schedule> =
        col.whereGreaterThan("startAt", now).get().await().documents.mapNotNull(::from).filter { !it.done }

    suspend fun get(id: Long): Schedule? = from(col.document(id.toString()).get().await())

    fun insert(schedule: Schedule): Long {
        val id = if (schedule.id == 0L) newId() else schedule.id
        col.document(id.toString()).set(schedule.copy(id = id).toMap()).logFailure("일정 저장")
        return id
    }
    fun update(schedule: Schedule) { col.document(schedule.id.toString()).set(schedule.toMap()).logFailure("일정 수정") }
    fun delete(schedule: Schedule) { col.document(schedule.id.toString()).delete().logFailure("일정 삭제") }
}
