package com.hmgcontest.probe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** 백그라운드 상주 검증용 포그라운드 서비스. 앱 화면이 사라져도 리스너가 살아있는지 확인. */
class ProbeService : Service() {
    private var probe: Probe? = null

    override fun onCreate() {
        super.onCreate()
        ProbeLog.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Drive probe", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Drive probe recording")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        runCatching { startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onFailure { ProbeLog.i("SVC", "startForeground failed: $it") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (probe == null) {
            probe = Probe(this).also { it.start(intent?.getStringExtra(EXTRA_REASON) ?: "restart") }
        } else if (intent?.action == ACTION_NAVI) {
            probe?.queryNavi()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ProbeLog.i("SVC", "onDestroy")
        probe?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "probe"
        const val EXTRA_REASON = "reason"
        const val ACTION_NAVI = "navi"

        fun start(context: Context, reason: String, action: String? = null) {
            val i = Intent(context, ProbeService::class.java).putExtra(EXTRA_REASON, reason).setAction(action)
            context.startForegroundService(i)
        }
    }
}
