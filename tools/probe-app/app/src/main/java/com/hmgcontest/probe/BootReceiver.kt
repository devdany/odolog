package com.hmgcontest.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 시동(콜드부트) 후 자동 시작 검증. suspend→resume에서는 호출되지 않는 것이 예상 동작. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ProbeLog.init(context)
        ProbeLog.i("BOOT", "received ${intent.action}")
        runCatching { ProbeService.start(context, "boot:${intent.action}") }
            .onFailure { ProbeLog.i("BOOT", "start service failed: $it") }
    }
}
