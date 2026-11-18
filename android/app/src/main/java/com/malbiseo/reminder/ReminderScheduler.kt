package com.malbiseo.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.malbiseo.data.Schedule

object ReminderScheduler {
    const val CHANNEL_ID = "schedule_reminders"
    const val EXTRA_ID = "schedule_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_NOTE = "note"
    const val EXTRA_START = "start_at"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** 일정 id(Long)를 알림/요청 코드용 Int 로 */
    fun codeOf(id: Long): Int = (id xor (id ushr 32)).toInt()

    /** 일정 알림 예약 (기존 예약은 덮어씀) */
    fun schedule(context: Context, s: Schedule) {
        cancel(context, s.id)
        if (s.done || s.remindBeforeMin < 0) return
        val at = s.startAt - s.remindBeforeMin * 60_000L
        if (at <= System.currentTimeMillis()) return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, s.id)
            .putExtra(EXTRA_TITLE, s.title)
            .putExtra(EXTRA_NOTE, s.note)
            .putExtra(EXTRA_START, s.startAt)
        val pi = PendingIntent.getBroadcast(
            context, codeOf(s.id), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // 정확 알람 권한 없이 쓸 수 있는 방식 (절전 모드에서 몇 분 늦을 수 있음)
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, id: Long) {
        val pi = PendingIntent.getBroadcast(
            context, codeOf(id), Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
