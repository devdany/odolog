package com.hidany.odolog.record

import android.content.Context
import com.hidany.odolog.Graph
import com.hidany.odolog.data.DriverEntity
import com.hidany.odolog.data.PlaceEntity
import com.hidany.odolog.data.PlaceKind
import com.hidany.odolog.data.Purpose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** 사용자 확정 동작. 화면과 알림 액션이 같은 경로를 쓴다. */
class TripActions(private val context: Context, private val scope: CoroutineScope) {
    private val db get() = Graph.db

    fun confirmPurpose(tripId: String, purpose: Purpose) = scope.launch {
        val t = db.trips().get(tripId) ?: return@launch
        var updated = t.copy(purpose = purpose, confirmedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        if (purpose == Purpose.PERSONAL) {
            // 개인 용도 운행은 위치를 남기지 않는다 — 거리만 보존
            updated = updated.copy(startAddr = null, endAddr = null, destName = null,
                startLat = null, startLng = null, endLat = null, endLng = null)
        }
        if (purpose == Purpose.BUSINESS && t.endLat != null && t.endLng != null) {
            learnBusinessPlace(t.endLat, t.endLng, t.destName ?: t.endAddr ?: "업무 장소")
        }
        db.trips().upsert(updated)
        if (purpose != Purpose.UNCONFIRMED) Graph.prefs.lastPurpose = purpose
        Notifier.cancelArrival(context, tripId)
        Graph.sync.requestSync()
    }

    fun setDriver(tripId: String, driver: DriverEntity?) = scope.launch {
        val t = db.trips().get(tripId) ?: return@launch
        db.trips().upsert(t.copy(driverId = driver?.id, driverName = driver?.name, updatedAt = System.currentTimeMillis()))
        Graph.sync.requestSync()
    }

    fun updateNote(tripId: String, note: String) = scope.launch {
        val t = db.trips().get(tripId) ?: return@launch
        db.trips().upsert(t.copy(note = note.ifBlank { null }, updatedAt = System.currentTimeMillis()))
        Graph.sync.requestSync()
    }

    /** 오늘 운전자 선택. 열린 운행이 있으면 그 운행에도 반영. */
    fun selectCurrentDriver(driver: DriverEntity?) = scope.launch {
        Graph.prefs.currentDriverId = driver?.id
        db.trips().open()?.let { setDriver(it.id, driver) }
    }

    fun addLocalDriver(name: String, dept: String?) = scope.launch {
        val d = DriverEntity(id = "local-" + UUID.randomUUID(), name = name.trim(), dept = dept?.trim()?.ifBlank { null })
        db.drivers().insert(d)
        if (Graph.prefs.currentDriverId == null) Graph.prefs.currentDriverId = d.id
    }

    fun removeDriver(id: String) = scope.launch { db.drivers().delete(id) }

    /** 현재 위치를 집/회사로 등록 (내비 즐겨찾기가 없을 때). */
    fun setPlaceHere(kind: PlaceKind, locator: suspend () -> GeoPoint?) = scope.launch {
        val loc = locator() ?: return@launch
        db.places().deleteKind(kind)
        db.places().insert(PlaceEntity(label = loc.address ?: kind.name, lat = loc.lat, lng = loc.lng, kind = kind))
    }

    fun removePlace(id: Long) = scope.launch { db.places().delete(id) }

    private suspend fun learnBusinessPlace(lat: Double, lng: Double, label: String) {
        val known = db.places().all().any { PurposeClassifier.distanceM(lat, lng, it.lat, it.lng) <= it.radiusM }
        if (!known) db.places().insert(PlaceEntity(label = label, lat = lat, lng = lng, kind = PlaceKind.BUSINESS))
    }
}
