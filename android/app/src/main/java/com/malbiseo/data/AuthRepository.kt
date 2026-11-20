package com.malbiseo.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val user: User) : AuthState
}

class AuthException(message: String) : Exception(message)

/**
 * Firebase Authentication 으로 로그인한다.
 * 사용자는 "아이디"만 입력하고, 내부적으로는 "아이디@malbiseo.app" 형태의 이메일 계정으로 만든다.
 * 비밀번호는 Firebase 가 관리하며 앱이나 Firestore 에는 저장하지 않는다.
 * 로그인 상태는 Firebase 가 기기에 기억해서 앱을 다시 켜도 유지된다.
 */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val profileVersion = MutableStateFlow(0)   // 이름 변경 후 화면 갱신용

    private val firebaseUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * 로그인 상태. 화면에는 아이디 대신 이름을 보여주므로 이름을 아래 순서로 찾는다.
     *  1) Firebase 프로필 이름  2) Firestore users/{uid} 의 name  3) 둘 다 없으면 아이디
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<AuthState> = combine(firebaseUser, profileVersion) { u, _ -> u?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(AuthState.LoggedOut)
            else combine(profileName(uid), profileVersion) { storedName, _ ->
                val u = auth.currentUser ?: return@combine AuthState.LoggedOut
                val id = loginIdOf(u)
                val name = u.displayName?.takeIf { it.isNotBlank() }
                    ?: storedName?.takeIf { it.isNotBlank() }
                    ?: id
                AuthState.LoggedIn(User(id = u.uid, name = name, loginId = id))
            }
        }

    /** Firestore 에 저장된 이름 (없거나 오류면 null) */
    private fun profileName(uid: String): Flow<String?> = callbackFlow {
        val reg = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
            trySend(snap?.getString("name"))
        }
        awaitClose { reg.remove() }
    }.onStart { emit(null) }

    /** 이름 바꾸기 (프로필과 Firestore 모두) */
    suspend fun updateName(newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) throw AuthException("이름을 입력해 주세요.")
        val u = auth.currentUser ?: throw AuthException("로그인이 필요해요.")
        try {
            u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(n).build()).await()
        } catch (e: Exception) {
            throw friendly(e)
        }
        db.collection("users").document(u.uid).set(mapOf("name" to n), SetOptions.merge())
        profileVersion.value++
    }

    suspend fun signUp(name: String, loginId: String, password: String, confirm: String) {
        val n = name.trim()
        val id = normalize(loginId)
        if (n.isEmpty()) throw AuthException("이름을 입력해 주세요.")
        if (!ID_RULE.matches(id)) throw AuthException("아이디는 영문 소문자·숫자·_ 4~20자로 입력해 주세요.")
        if (password.length < 6) throw AuthException("비밀번호는 6자 이상으로 입력해 주세요.")
        if (password != confirm) throw AuthException("비밀번호가 서로 달라요.")
        try {
            val user = auth.createUserWithEmailAndPassword(emailOf(id), password).await().user
                ?: throw AuthException("가입에 실패했어요. 다시 시도해 주세요.")
            db.collection("users").document(user.uid)
                .set(mapOf("name" to n, "loginId" to id, "createdAt" to System.currentTimeMillis()))
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(n).build()).await()
            profileVersion.value++
        } catch (e: Exception) {
            throw friendly(e)
        }
    }

    suspend fun login(loginId: String, password: String) {
        val id = normalize(loginId)
        if (id.isEmpty() || password.isEmpty()) throw AuthException("아이디와 비밀번호를 입력해 주세요.")
        if (!ID_RULE.matches(id)) throw AuthException("아이디 또는 비밀번호가 맞지 않아요.")
        try {
            auth.signInWithEmailAndPassword(emailOf(id), password).await()
        } catch (e: Exception) {
            throw friendly(e)
        }
    }

    fun logout() = auth.signOut()

    /**
     * 회원 탈퇴: 비밀번호로 본인 확인 → 메모·회의록·일정과 프로필을 Firestore 에서 모두 삭제 → 로그인 계정 삭제.
     * 데이터를 먼저 지우고 계정을 마지막에 지운다 (계정이 먼저 없어지면 보안 규칙상 데이터를 지울 수 없음).
     */
    suspend fun deleteAccount(password: String) {
        val u = auth.currentUser ?: throw AuthException("로그인이 필요해요.")
        if (password.isEmpty()) throw AuthException("비밀번호를 입력해 주세요.")
        try {
            val email = u.email ?: throw AuthException("계정 정보를 찾을 수 없어요.")
            u.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()

            val userDoc = db.collection("users").document(u.uid)
            for (name in USER_COLLECTIONS) {
                val col = userDoc.collection(name)
                while (true) {
                    val snap = col.limit(400).get().await()
                    if (snap.isEmpty) break
                    val batch = db.batch()
                    snap.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            userDoc.delete().await()
            u.delete().await()
        } catch (e: Exception) {
            throw friendly(e)
        }
    }

    private fun friendly(e: Exception): AuthException = when (e) {
        is AuthException -> e
        is FirebaseAuthUserCollisionException -> AuthException("이미 사용 중인 아이디예요.")
        is FirebaseAuthWeakPasswordException -> AuthException("비밀번호는 6자 이상으로 입력해 주세요.")
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> AuthException("아이디 또는 비밀번호가 맞지 않아요.")
        is FirebaseAuthRecentLoginRequiredException -> AuthException("보안을 위해 다시 로그인한 뒤 시도해 주세요.")
        is FirebaseNetworkException -> AuthException("인터넷 연결을 확인해 주세요.")
        is FirebaseTooManyRequestsException -> AuthException("시도가 너무 많아요. 잠시 후 다시 시도해 주세요.")
        else -> AuthException("문제가 생겼어요. (${e.message ?: e.javaClass.simpleName})")
    }

    private fun normalize(s: String) = s.trim().lowercase()
    private fun emailOf(id: String) = "$id@$DOMAIN"
    private fun loginIdOf(u: FirebaseUser) = u.email?.substringBefore('@') ?: u.uid.take(8)

    companion object {
        private const val DOMAIN = "malbiseo.app"
        private val ID_RULE = Regex("^[a-z0-9_]{4,20}$")
        private val USER_COLLECTIONS = listOf("memos", "meetings", "schedules")
    }
}
