package com.hidany.odolog

import android.content.Context
import com.hidany.odolog.data.AppDatabase
import com.hidany.odolog.data.Prefs
import com.hidany.odolog.record.TripActions
import com.hidany.odolog.sync.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 화면에 보여줄 실시간 차량·기록 상태. */
data class LiveStatus(
    val serviceRunning: Boolean = false,
    val odometerKm: Double? = null,
    val moving: Boolean? = null,
    val restricted: Boolean = false,
    val openTripId: String? = null,
    val stoppingUntil: Long? = null,
)

class LiveState {
    private val _state = MutableStateFlow(LiveStatus())
    val state: StateFlow<LiveStatus> = _state
    fun update(f: (LiveStatus) -> LiveStatus) = _state.update(f)
}

/** 수동 DI. Application.onCreate에서 init. */
object Graph {
    lateinit var appContext: Context; private set
    lateinit var db: AppDatabase; private set
    lateinit var prefs: Prefs; private set
    lateinit var sync: SyncClient; private set
    lateinit var actions: TripActions; private set
    val live = LiveState()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.get(appContext)
        prefs = Prefs(appContext)
        sync = SyncClient(appContext, scope)
        actions = TripActions(appContext, scope)
    }
}
