package com.hidany.odolog.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hidany.odolog.Graph
import com.hidany.odolog.R
import com.hidany.odolog.data.Purpose
import com.hidany.odolog.data.TripEntity
import com.hidany.odolog.data.TripSource
import com.hidany.odolog.ui.MainActivity
import com.hidany.odolog.ui.fmtKm

/**
 * 도착 1탭: 알림의 액션 버튼(출퇴근/업무/개인)으로 앱을 열지 않고 용도를 확정한다.
 * 알림은 운행 종료(=정차 후) 시점에만 올리므로 주행 중 조작을 요구하지 않는다.
 */
object Notifier {
    const val CH_RECORDING = "recording"
    const val CH_ARRIVAL = "arrival"
    const val RECORDING_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RECORDING, "운행 기록 중", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ARRIVAL, "도착 · 용도 확인", NotificationManager.IMPORTANCE_HIGH))
    }

    fun recording(context: Context): Notification =
        Notification.Builder(context, CH_RECORDING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("오도로그가 계기판 거리를 기록하고 있습니다")
            .setContentIntent(openApp(context, null))
            .setOngoing(true)
            .build()

    fun arrival(context: Context, trip: TripEntity, parked: Boolean = false) {
        val title = when {
            trip.source == TripSource.GAP -> "기록되지 않은 ${fmtKm(trip.distanceKm)}km가 있습니다"
            parked -> "내리기 전에 용도를 선택하세요 · ${fmtKm(trip.distanceKm)}km"
            else -> "도착 · ${fmtKm(trip.distanceKm)}km"
        }
        val route = listOfNotNull(trip.startAddr, trip.destName ?: trip.endAddr).joinToString(" → ")
        val suggest = trip.suggestedPurpose?.let { "추천: ${it.label}" }
        val text = listOfNotNull(route.ifBlank { null }, suggest).joinToString(" · ").ifBlank { "용도를 선택해 주세요" }
        val b = Notification.Builder(context, CH_ARRIVAL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, trip.id))
            .setAutoCancel(true)
        listOf(Purpose.COMMUTE, Purpose.BUSINESS, Purpose.PERSONAL).forEach { p ->
            val label = if (p == trip.suggestedPurpose) "✓ ${p.label}" else p.label
            b.addAction(Notification.Action.Builder(null, label, purposeIntent(context, trip.id, p)).build())
        }
        context.getSystemService(NotificationManager::class.java).notify(trip.id.hashCode(), b.build())
    }

    fun cancelArrival(context: Context, tripId: String) =
        context.getSystemService(NotificationManager::class.java).cancel(tripId.hashCode())

    fun cancelAllArrivals(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.notification.channelId == CH_ARRIVAL }.forEach { nm.cancel(it.id) }
    }

    private fun openApp(context: Context, tripId: String?) = PendingIntent.getActivity(
        context, tripId?.hashCode() ?: 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { tripId?.let { putExtra(MainActivity.EXTRA_TRIP, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun purposeIntent(context: Context, tripId: String, p: Purpose) = PendingIntent.getBroadcast(
        context, (tripId + p.name).hashCode(),
        Intent(context, PurposeActionReceiver::class.java).putExtra("trip", tripId).putExtra("purpose", p.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class PurposeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trip = intent.getStringExtra("trip") ?: return
        val p = intent.getStringExtra("purpose")?.let { Purpose.valueOf(it) } ?: return
        Graph.actions.confirmPurpose(trip, p)
    }
}
