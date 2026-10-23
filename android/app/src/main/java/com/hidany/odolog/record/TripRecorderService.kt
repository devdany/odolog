package com.hidany.odolog.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hidany.odolog.Graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 시동(부팅)부터 상주하며 운행을 기록하는 포그라운드 서비스.
 * 폴링 없이 리스너만 사용해 차량 리소스 기준(주행 중 CPU 5% 미만)을 지킨다.
 */
class TripRecorderService : LifecycleService() {
    private var signals: VehicleSignals? = null
    private var navi: NaviLocator? = null
    private var engine: TripEngine? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(Notifier.RECORDING_ID, Notifier.recording(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val n = NaviLocator(this).also { navi = it }
        lateinit var s: VehicleSignals
        val e = TripEngine(
            scope = lifecycleScope,
            navi = n,
            readOdometer = { cb -> s.readOdometer(cb = cb) },
            onArrival = { trip -> Notifier.arrival(this, trip) },
        ).also { engine = it }
        s = VehicleSignals(
            context = this,
            onGear = { moving, _ -> e.onGear(moving) },
            onOdometer = { km -> e.onOdometer(km) },
            onRestricted = { r -> Graph.live.update { it.copy(restricted = r) } },
        ).also { signals = it }

        n.start()
        s.start()
        Graph.live.update { it.copy(serviceRunning = true) }
        // Vehicle SDK 연결 대기 후 체인 대사
        lifecycleScope.launch { delay(2000); e.onStart() }
        // 주기 동기화 (페어링된 경우에만 실제 전송)
        lifecycleScope.launch { while (true) { Graph.sync.requestSync(); delay(5 * 60_000L) } }
    }

    fun locator(): NaviLocator? = navi

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        signals?.stop()
        navi?.stop()
        Graph.live.update { it.copy(serviceRunning = false) }
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: TripRecorderService? = null; private set
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TripRecorderService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TripRecorderService.start(context)
    }
}
