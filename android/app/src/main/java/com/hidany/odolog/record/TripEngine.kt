package com.hidany.odolog.record

import android.util.Log
import com.hidany.odolog.Graph
import com.hidany.odolog.data.ChainEventEntity
import com.hidany.odolog.data.Purpose
import com.hidany.odolog.data.TripEntity
import com.hidany.odolog.data.TripSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 운행 상태 머신 (docs/spec.md 3장).
 *
 *   PARKED ──D/R──▶ DRIVING ──P──▶ STOPPING(유예) ──유예 만료──▶ 운행 종료(도착 카드)
 *                      ▲               │ D/R (유예 중)
 *                      └───────────────┘ 같은 운행 계속
 *
 * 오도미터 체인: 새 운행 시작/앱 시작 시 마지막 관측값과 현재 계기판을 비교해
 * 증가분이 있으면 "미확인 구간"(source=GAP) 운행을 만들어 공백을 메운다. 감소하면 체인 리셋 이벤트.
 */
class TripEngine(
    private val scope: CoroutineScope,
    private val navi: NaviLocator,
    private val readOdometer: ((Double?) -> Unit) -> Unit,
    private val onArrival: (TripEntity) -> Unit,
) {
    private val db = Graph.db
    private val prefs = Graph.prefs
    private val lock = Mutex()
    private var currentOdo: Double? = null
    private var gearMoving: Boolean? = null
    private var stopJob: Job? = null

    /** 서비스 시작 시: 열린 운행 복구 + 체인 대사. */
    fun onStart() {
        readOdometer { km ->
            scope.launch {
                lock.withLock {
                    if (km != null) { currentOdo = km; Graph.live.update { it.copy(odometerKm = km) } }
                    val open = db.trips().open()
                    if (open == null) {
                        km?.let { reconcile(it) }
                    } else if (prefs.stoppingSince > 0) {
                        scheduleFinish(open.id)
                    }
                    refreshLive()
                }
            }
        }
    }

    fun onGear(moving: Boolean) {
        if (gearMoving == moving) return
        gearMoving = moving
        Graph.live.update { it.copy(moving = moving) }
        scope.launch {
            lock.withLock { if (moving) handleDrive() else handlePark() }
            refreshLive()
        }
    }

    fun onOdometer(km: Double) {
        currentOdo = km
        Graph.live.update { it.copy(odometerKm = km) }
        scope.launch {
            lock.withLock {
                val open = db.trips().open()
                if (open != null) {
                    if (km >= open.startOdoKm) db.trips().upsert(open.copy(endOdoKm = km, updatedAt = System.currentTimeMillis()))
                } else if (gearMoving != true) {
                    // 주차 중 계기판이 변했다면(앱이 놓친 주행) 즉시 체인 대사
                    reconcile(km)
                }
                prefs.lastOdoKm = km; prefs.lastOdoAt = System.currentTimeMillis()
            }
        }
    }

    private suspend fun handleDrive() {
        val open = db.trips().open()
        if (open != null) {
            // 유예 중 재출발 → 같은 운행 계속
            stopJob?.cancel(); stopJob = null; prefs.stoppingSince = 0
            return
        }
        // 시동 직후엔 기어 이벤트가 첫 계기판 값보다 먼저 올 수 있다
        val odo = currentOdo ?: awaitOdometer()?.also { currentOdo = it }
            ?: prefs.lastOdoKm.takeIf { it >= 0 } ?: run { Log.w(TAG, "no odometer yet"); return }
        reconcile(odo)
        val driver = prefs.currentDriverId?.let { id -> db.drivers().all().firstOrNull { it.id == id } }
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            startAt = System.currentTimeMillis(),
            startOdoKm = odo,
            endOdoKm = odo,
            driverId = driver?.id,
            driverName = driver?.name,
        )
        db.trips().upsert(trip)
        prefs.lastOdoKm = odo; prefs.lastOdoAt = trip.startAt
        Log.i(TAG, "trip started ${trip.id} at $odo km")
        navi.refreshDestination()
        scope.launch {
            val loc = navi.currentLocation() ?: return@launch
            db.trips().get(trip.id)?.let { db.trips().upsert(it.copy(startAddr = loc.address, startLat = loc.lat, startLng = loc.lng, updatedAt = System.currentTimeMillis())) }
        }
        Graph.sync.requestSync()
    }

    private suspend fun handlePark() {
        val open = db.trips().open() ?: return
        prefs.stoppingSince = System.currentTimeMillis()
        navi.refreshDestination()
        scheduleFinish(open.id)
    }

    private fun scheduleFinish(tripId: String) {
        stopJob?.cancel()
        val since = prefs.stoppingSince
        val waitMs = (since + prefs.stopGraceSec * 1000L - System.currentTimeMillis()).coerceAtLeast(0)
        Graph.live.update { it.copy(stoppingUntil = since + prefs.stopGraceSec * 1000L) }
        stopJob = scope.launch {
            delay(waitMs)
            lock.withLock { finish(tripId, since) }
            refreshLive()
        }
    }

    private suspend fun finish(tripId: String, endAt: Long) {
        val trip = db.trips().get(tripId) ?: return
        if (!trip.isOpen) return
        prefs.stoppingSince = 0
        val endOdo = maxOf(currentOdo ?: trip.endOdoKm ?: trip.startOdoKm, trip.startOdoKm)
        val end = navi.currentLocation()
        val dest = navi.destination
        val start = trip.startLat?.let { GeoPoint(it, trip.startLng ?: 0.0, trip.startAddr) }
        val s = PurposeClassifier.suggest(
            start = start, end = end, places = db.places().all(),
            naviHome = navi.home, naviOffice = navi.office,
            hadDestination = dest != null, lastPurpose = prefs.lastPurpose,
        )
        val done = trip.copy(
            endAt = endAt,
            endOdoKm = endOdo,
            endAddr = end?.address,
            endLat = end?.lat,
            endLng = end?.lng,
            destName = dest?.name?.ifBlank { null } ?: s.placeLabel,
            tollWon = dest?.tollFare?.takeIf { it > 0 },
            suggestedPurpose = s.purpose,
            updatedAt = System.currentTimeMillis(),
        )
        db.trips().upsert(done)
        prefs.lastOdoKm = endOdo; prefs.lastOdoAt = endAt
        Log.i(TAG, "trip finished ${done.id} ${done.distanceKm} km, suggest ${s.purpose}")
        onArrival(done)
        Graph.sync.requestSync()
    }

    /** 체인 대사: 마지막 관측값 대비 증가 → 미확인 구간, 감소 → 리셋 이벤트. */
    private suspend fun reconcile(nowKm: Double) {
        val last = prefs.lastOdoKm
        val now = System.currentTimeMillis()
        when {
            last < 0 -> Unit // 최초 관측
            nowKm - last > GAP_THRESHOLD_KM -> {
                val gap = TripEntity(
                    id = UUID.randomUUID().toString(),
                    startAt = prefs.lastOdoAt.takeIf { it > 0 } ?: now,
                    endAt = now,
                    startOdoKm = last,
                    endOdoKm = nowKm,
                    source = TripSource.GAP,
                    purpose = Purpose.UNCONFIRMED,
                )
                db.trips().upsert(gap)
                Log.i(TAG, "gap trip ${gap.distanceKm} km")
                onArrival(gap)
                Graph.sync.requestSync()
            }
            last - nowKm > GAP_THRESHOLD_KM -> {
                db.chain().insert(ChainEventEntity(at = now, type = "RESET", fromKm = last, toKm = nowKm))
                Log.w(TAG, "odometer decreased $last -> $nowKm (chain reset)")
            }
        }
        prefs.lastOdoKm = nowKm; prefs.lastOdoAt = now
    }

    private suspend fun awaitOdometer(): Double? = suspendCancellableCoroutine { cont ->
        readOdometer { km -> if (cont.isActive) cont.resume(km) }
    }

    private suspend fun refreshLive() {
        val open = db.trips().open()
        Graph.live.update {
            it.copy(openTripId = open?.id, stoppingUntil = if (prefs.stoppingSince > 0) prefs.stoppingSince + prefs.stopGraceSec * 1000L else null)
        }
    }

    companion object {
        private const val TAG = "OdoLog.Engine"
        const val GAP_THRESHOLD_KM = 0.05
    }
}
