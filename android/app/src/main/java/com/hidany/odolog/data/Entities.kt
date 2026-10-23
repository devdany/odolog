package com.hidany.odolog.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Purpose(val label: String) {
    COMMUTE("출퇴근"),
    BUSINESS("업무"),
    PERSONAL("개인"),
    UNCONFIRMED("미확정");
}

enum class TripSource { AUTO, GAP }

/**
 * 운행 1건 = 운행기록부 1행.
 * 거리는 항상 계기판(오도미터) 차분으로만 계산한다 — docs/spec.md 3장.
 */
@Entity(tableName = "trips", indices = [Index("startAt")])
data class TripEntity(
    @PrimaryKey val id: String,
    val startAt: Long,
    val endAt: Long? = null,
    val startOdoKm: Double,
    val endOdoKm: Double? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val purpose: Purpose = Purpose.UNCONFIRMED,
    val suggestedPurpose: Purpose? = null,
    val startAddr: String? = null,
    val endAddr: String? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val destName: String? = null,
    val tollWon: Int? = null,
    val note: String? = null,
    val source: TripSource = TripSource.AUTO,
    val confirmedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
) {
    val distanceKm: Double get() = ((endOdoKm ?: startOdoKm) - startOdoKm).coerceAtLeast(0.0)
    val isOpen: Boolean get() = endAt == null
    val needsAttention: Boolean get() = !isOpen && purpose == Purpose.UNCONFIRMED
}

/** 운전자. 페어링 전에는 로컬 운전자(id "local-…"), 페어링 후에는 서버 운전자 목록으로 대체. */
@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dept: String? = null,
    val fromServer: Boolean = false,
)

/** 학습된 장소(업무 장소, 집, 회사). */
@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Double = 300.0,
    val kind: PlaceKind,
)

enum class PlaceKind { HOME, OFFICE, BUSINESS }

/** 체인 이상 기록(계기판 감소 등). */
@Entity(tableName = "chain_events")
data class ChainEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    val fromKm: Double,
    val toKm: Double,
)
