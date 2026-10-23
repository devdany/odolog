package com.hidany.odolog.record

import com.hidany.odolog.data.PlaceEntity
import com.hidany.odolog.data.PlaceKind
import com.hidany.odolog.data.Purpose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 도착 시 추천 용도 (docs/spec.md F4).
 * 1) 집↔회사 → 출퇴근  2) 학습된 업무 장소 → 업무  3) 내비 목적지가 있던 운행 → 업무
 * 4) 그 외 → 마지막으로 선택한 용도(없으면 업무)
 */
object PurposeClassifier {
    data class Result(val purpose: Purpose, val placeLabel: String?)

    fun suggest(
        start: GeoPoint?,
        end: GeoPoint?,
        places: List<PlaceEntity>,
        naviHome: GeoPoint?,
        naviOffice: GeoPoint?,
        hadDestination: Boolean,
        lastPurpose: Purpose?,
    ): Result {
        val homes = places.filter { it.kind == PlaceKind.HOME }.map { it.lat to it.lng } + listOfNotNull(naviHome?.let { it.lat to it.lng })
        val offices = places.filter { it.kind == PlaceKind.OFFICE }.map { it.lat to it.lng } + listOfNotNull(naviOffice?.let { it.lat to it.lng })
        fun near(p: GeoPoint?, list: List<Pair<Double, Double>>) = p != null && list.any { distanceM(p.lat, p.lng, it.first, it.second) <= 300 }

        if ((near(start, homes) && near(end, offices)) || (near(start, offices) && near(end, homes))) {
            return Result(Purpose.COMMUTE, null)
        }
        if (end != null) {
            places.filter { it.kind == PlaceKind.BUSINESS }
                .firstOrNull { distanceM(end.lat, end.lng, it.lat, it.lng) <= it.radiusM }
                ?.let { return Result(Purpose.BUSINESS, it.label) }
        }
        if (hadDestination) return Result(Purpose.BUSINESS, null)
        return Result(lastPurpose?.takeIf { it != Purpose.UNCONFIRMED } ?: Purpose.BUSINESS, null)
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
