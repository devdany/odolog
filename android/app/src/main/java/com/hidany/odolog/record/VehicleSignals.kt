package com.hidany.odolog.record

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener

/**
 * 차량 신호 수집. 에뮬레이터 실측(docs/tech-review.md) 기준으로 채택한 경로만 사용한다.
 * - 기어: AAOS CarPropertyManager GEAR_SELECTION (Pleos SDK의 Gear는 deprecated)
 * - 계기판 거리: Pleos Vehicle SDK Odometer (AAOS PERF_ODOMETER는 3rd party 권한 없음)
 * - 주행 규제: CarUxRestrictionsManager (보조, 화면 표시용)
 */
class VehicleSignals(
    private val context: Context,
    private val onGear: (moving: Boolean, raw: Int) -> Unit,
    private val onOdometer: (km: Double) -> Unit,
    private val onRestricted: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var vehicle: Vehicle? = null
    private var odoListener: DistanceDrivenListener? = null
    private var gearCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private var uxListener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null

    fun start() {
        startCar()
        startVehicle()
    }

    fun stop() {
        runCatching { odoListener?.let { vehicle?.getOdometer()?.unregisterDistanceDriven(it) } }
        runCatching { vehicle?.release() }
        runCatching {
            val c = car ?: return@runCatching
            gearCallback?.let { (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(it) }
            uxListener?.let { (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).unregisterListener() }
            c.disconnect()
        }
        vehicle = null; car = null
    }

    /** 현재 계기판 값을 1회 조회 (연결 직후 실패하면 재시도). */
    fun readOdometer(attempt: Int = 0, cb: (Double?) -> Unit) {
        val v = vehicle ?: return cb(null)
        v.getOdometer().getCurrentDistanceDriven(
            { km -> main.post { cb(km?.toDouble()) } },
            { e ->
                if (attempt < 5) main.postDelayed({ readOdometer(attempt + 1, cb) }, 1000)
                else { Log.w(TAG, "odometer read failed", e); main.post { cb(null) } }
            },
        )
    }

    private fun startCar() {
        val c = runCatching { Car.createCar(context) }.getOrNull() ?: run { Log.w(TAG, "Car unavailable"); return }
        car = c
        runCatching {
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val g = value.value as? Int ?: return
                    main.post { onGear(isMoving(g), g) }
                }
                override fun onErrorEvent(propId: Int, zone: Int) = Unit
            }
            gearCallback = cb
            cpm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        }.onFailure { Log.w(TAG, "gear unavailable", it) }
        runCatching {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            onRestricted(ux.currentCarUxRestrictions.isRequiresDistractionOptimization)
            val l = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
                main.post { onRestricted(r.isRequiresDistractionOptimization) }
            }
            uxListener = l
            ux.registerListener(l)
        }.onFailure { Log.w(TAG, "ux restrictions unavailable", it) }
    }

    private fun startVehicle() {
        val v = Vehicle(context).also { vehicle = it }
        runCatching { v.initialize() }.onFailure { Log.w(TAG, "Vehicle SDK init failed", it); return }
        // initialize()는 연결 콜백이 없어 잠시 후 구독
        main.postDelayed({
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(value: Float?) {
                    value?.let { km -> main.post { onOdometer(km.toDouble()) } }
                }
                override fun onFailed(e: Exception) { Log.w(TAG, "odometer listener failed", e) }
            }
            odoListener = l
            runCatching { v.getOdometer().registerDistanceDriven(l) }.onFailure { Log.w(TAG, "odometer register failed", it) }
        }, 1500)
    }

    companion object {
        private const val TAG = "OdoLog.Signals"
        fun isMoving(gear: Int) = gear != VehicleGear.GEAR_PARK && gear != VehicleGear.GEAR_UNKNOWN
    }
}
