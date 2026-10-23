package com.hidany.odolog.record

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.constants.BookmarkType
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

data class GeoPoint(val lat: Double, val lng: Double, val address: String?)

/**
 * 2층(목적 보완) 데이터. 내비가 없거나 응답이 없어도 기록은 계속돼야 하므로 모든 조회는 실패 허용.
 * 실측: 경로 미설정 상태에서도 getCurrentLocationInfo가 주소를 준다. 부팅 직후엔 무응답 → 재시도.
 */
class NaviLocator(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    @Volatile var connected = false; private set
    private var pendingLocation: CompletableDeferred<CurrentLocationInfo>? = null

    @Volatile var home: GeoPoint? = null; private set
    @Volatile var office: GeoPoint? = null; private set
    /** 현재 경로 안내 중인 목적지 (안내 중일 때만). */
    @Volatile var destination: DestinationInfo? = null; private set
    @Volatile var lastArrived: DestinationArrivedInfo? = null; private set

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            pendingLocation?.complete(currentLocationInfo)
        }
        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
            fun pick(t: BookmarkType) = bookmarkInfo.firstOrNull { it.category == t && (it.latitude != 0.0 || it.longitude != 0.0) }
                ?.let { GeoPoint(it.latitude, it.longitude, it.address) }
            home = pick(BookmarkType.HOME); office = pick(BookmarkType.OFFICE)
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) { destination = destinationInfo }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { lastArrived = destinationArrivedInfo }
        override fun onRouteCancelled() { destination = null }
        override fun onBookMarkChanged() { runCatching { navi.getBookmarkInfo() } }
    }

    fun start() {
        runCatching {
            navi.initialize(
                {
                    connected = true
                    navi.addListener(listener)
                    runCatching { navi.getBookmarkInfo() }
                    runCatching { navi.getDestinationInfo() }
                },
                { t -> Log.w(TAG, "navi connect failed", t) },
            )
        }.onFailure { Log.w(TAG, "navi unavailable", it) }
    }

    fun stop() {
        runCatching { navi.removeListener(listener); navi.release() }
    }

    fun refreshDestination() { if (connected) runCatching { navi.getDestinationInfo() } }

    /** 현재 위치·주소. 최대 3회, 회당 3초. */
    suspend fun currentLocation(): GeoPoint? {
        repeat(3) {
            if (connected) {
                val d = CompletableDeferred<CurrentLocationInfo>()
                pendingLocation = d
                main.post { runCatching { navi.getCurrentLocationInfo() } }
                val r = withTimeoutOrNull(3000) { d.await() }
                if (r != null && (r.latitude != 0.0 || r.longitude != 0.0)) {
                    return GeoPoint(r.latitude, r.longitude, r.address.ifBlank { null })
                }
            } else {
                kotlinx.coroutines.delay(1500)
            }
        }
        return null
    }

    companion object { private const val TAG = "OdoLog.Navi" }
}
