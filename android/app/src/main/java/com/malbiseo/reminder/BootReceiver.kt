package com.malbiseo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.malbiseo.data.UserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** 재부팅하면 알람이 사라지므로 로그인한 사용자의 일정을 다시 예약 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    UserData(uid).schedules.upcoming(System.currentTimeMillis())
                        .forEach { ReminderScheduler.schedule(context, it) }
                }
            } catch (e: Exception) {
                Log.w("BootReceiver", "알림 재예약 실패", e)
            } finally {
                pending.finish()
            }
        }
    }
}
