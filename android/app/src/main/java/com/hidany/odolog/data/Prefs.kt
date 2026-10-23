package com.hidany.odolog.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 작은 설정·체인 상태. 화면이 구독할 값은 StateFlow로도 노출한다. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("odolog", Context.MODE_PRIVATE)

    /** 오도미터 체인의 마지막 관측값(km)과 시각. 앱이 꺼져 있던 동안의 증가분 = 미확인 구간. */
    var lastOdoKm: Double
        get() = sp.getString("last_odo", null)?.toDoubleOrNull() ?: -1.0
        set(v) = sp.edit { putString("last_odo", v.toString()) }
    var lastOdoAt: Long
        get() = sp.getLong("last_odo_at", 0)
        set(v) = sp.edit { putLong("last_odo_at", v) }

    /** 열린 운행의 P단 진입 시각(정차 유예 중). 0 = 없음. */
    var stoppingSince: Long
        get() = sp.getLong("stopping_since", 0)
        set(v) = sp.edit { putLong("stopping_since", v) }

    private val _currentDriverId = MutableStateFlow(sp.getString("current_driver", null))
    val currentDriverIdFlow: StateFlow<String?> = _currentDriverId
    var currentDriverId: String?
        get() = _currentDriverId.value
        set(v) { sp.edit { putString("current_driver", v) }; _currentDriverId.value = v }

    /** 현재 운전자의 서버 PIN 체크인 id (없으면 미확인). 시동을 끄면 운전자와 함께 해제. */
    private val _checkinId = MutableStateFlow(sp.getString("checkin_id", null))
    val checkinIdFlow: StateFlow<String?> = _checkinId
    var checkinId: String?
        get() = _checkinId.value
        set(v) { sp.edit { putString("checkin_id", v) }; _checkinId.value = v }
    var checkinAt: Long
        get() = sp.getLong("checkin_at", 0)
        set(v) = sp.edit { putLong("checkin_at", v) }

    /** 시동을 켠 채 P로 서 있을 때의 정차 유예(초). 기본 30분, 시연 15초. 시동을 끄면 유예 없이 종료. */
    private val _stopGraceSec = MutableStateFlow(sp.getInt("stop_grace_v2", 1800))
    val stopGraceSecFlow: StateFlow<Int> = _stopGraceSec
    var stopGraceSec: Int
        get() = _stopGraceSec.value
        set(v) { sp.edit { putInt("stop_grace_v2", v) }; _stopGraceSec.value = v }

    // ---- 페어링(관리자 웹 연동) ----
    var deviceToken: String?
        get() = sp.getString("device_token", null)
        set(v) = sp.edit { putString("device_token", v) }
    private val _vehicleLabel = MutableStateFlow(sp.getString("vehicle_label", null))
    val vehicleLabelFlow: StateFlow<String?> = _vehicleLabel
    var vehicleLabel: String?
        get() = _vehicleLabel.value
        set(v) { sp.edit { putString("vehicle_label", v) }; _vehicleLabel.value = v }
    private val _orgName = MutableStateFlow(sp.getString("org_name", null))
    val orgNameFlow: StateFlow<String?> = _orgName
    var orgName: String?
        get() = _orgName.value
        set(v) { sp.edit { putString("org_name", v) }; _orgName.value = v }
    private val _lastSyncAt = MutableStateFlow(sp.getLong("last_sync", 0))
    val lastSyncAtFlow: StateFlow<Long> = _lastSyncAt
    var lastSyncAt: Long
        get() = _lastSyncAt.value
        set(v) { sp.edit { putLong("last_sync", v) }; _lastSyncAt.value = v }

    var lastPurpose: Purpose?
        get() = sp.getString("last_purpose", null)?.let { Purpose.valueOf(it) }
        set(v) = sp.edit { putString("last_purpose", v?.name) }
}
