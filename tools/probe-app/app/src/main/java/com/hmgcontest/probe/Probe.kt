package com.hmgcontest.probe

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener

/**
 * 기술 검토 체크리스트(docs/tech-review.md 7장) 검증용.
 * 1 오도미터 / 2 CarUxRestrictions / 3 CarPropertyManager / 4 트립 세션 거리
 * 5 백그라운드·체인 대사 / 6·7 NaviHelper / 8 시트 위치
 */
class Probe(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("probe", Context.MODE_PRIVATE)
    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var car: Car? = null

    fun start(reason: String) {
        ProbeLog.i("START", "reason=$reason pid=${android.os.Process.myPid()}")
        startVehicle()
        startCar()
        startNavi()
    }

    fun stop() {
        runCatching { vehicle?.release() }
        runCatching { navi?.release() }
        runCatching { car?.disconnect() }
        vehicle = null; navi = null; car = null
        ProbeLog.i("STOP", "released")
    }

    // ---- 1, 4, 8: Pleos Vehicle SDK ----
    private fun startVehicle() {
        val v = Vehicle(context).also { vehicle = it }
        runCatching { v.initialize() }.onFailure { ProbeLog.i("VEH", "initialize failed: $it"); return }
        ProbeLog.i("VEH", "initialize called")
        // initialize()는 연결 콜백이 없으므로 잠시 후 조회
        main.postDelayed({ queryVehicle(v) }, 1500)
    }

    private fun queryVehicle(v: Vehicle) {
        val odo = v.getOdometer()
        odo.getCurrentDistanceDriven({ km -> ProbeLog.i("ODO", "current odometer = $km km"); reconcile(km) },
            { ProbeLog.i("ODO", "getCurrentDistanceDriven failed: $it") })
        odo.getTripCurrentDrivingDistance({ m -> ProbeLog.i("ODO", "trip current distance = $m m") },
            { ProbeLog.i("ODO", "getTripCurrentDrivingDistance failed: $it") })
        odo.getTripCurrentDrivingTime({ min -> ProbeLog.i("ODO", "trip current time = $min min") },
            { ProbeLog.i("ODO", "getTripCurrentDrivingTime failed: $it") })
        odo.getTripCumulativeDrivingDistance({ m -> ProbeLog.i("ODO", "trip cumulative distance = $m m") },
            { ProbeLog.i("ODO", "getTripCumulativeDrivingDistance failed: $it") })

        odo.registerDistanceDriven(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                ProbeLog.i("ODO", "listener odometer = $value km"); reconcile(value)
            }
            override fun onFailed(e: Exception) = ProbeLog.i("ODO", "registerDistanceDriven failed: $e")
        })
        odo.registerTripCurrentDrivingDistance(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) = ProbeLog.i("ODO", "listener trip current = $value m")
            override fun onFailed(e: Exception) = ProbeLog.i("ODO", "registerTripCurrentDrivingDistance failed: $e")
        })
        odo.registerTripCurrentDrivingTime(object : DrivingTimeListener {
            override fun onDrivingTimeUpdated(value: Int?) = ProbeLog.i("ODO", "listener trip time = $value min")
            override fun onFailed(e: Exception) = ProbeLog.i("ODO", "registerTripCurrentDrivingTime failed: $e")
        })

        // 문서에 없는 Gear 도메인 (SDK가 deprecated 표시: "use the Google GEAR_SELECTION property")
        @Suppress("DEPRECATION")
        runCatching {
            val gear = v.getGear()
            gear.getCurrentGearSelectionState({ g -> ProbeLog.i("GEAR-SDK", "current = $g") },
                { ProbeLog.i("GEAR-SDK", "get failed: $it") })
            gear.registerGearSelection(object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) =ProbeLog.i("GEAR-SDK", "listener = $gear")
                override fun onFailed(e: Exception) = ProbeLog.i("GEAR-SDK", "register failed: $e")
            })
        }.onFailure { ProbeLog.i("GEAR-SDK", "error: $it") }

        val driver = SeatArea(SeatValue.ROW_1_LEFT)
        val seat = v.getSeat()
        seat.getCurrentSeatForeAftPosition(driver, { ProbeLog.i("SEAT", "driver foreAft = $it") }, { ProbeLog.i("SEAT", "foreAft failed: $it") })
        seat.getCurrentSeatHeightPosition(driver, { ProbeLog.i("SEAT", "driver height = $it") }, { ProbeLog.i("SEAT", "height failed: $it") })
        seat.getCurrentSeatBackrestAnglePosition(driver, { ProbeLog.i("SEAT", "driver backrest = $it") }, { ProbeLog.i("SEAT", "backrest failed: $it") })
        seat.getCurrentSeatOccupancy(driver, { ProbeLog.i("SEAT", "driver occupied = $it") }, { ProbeLog.i("SEAT", "occupancy failed: $it") })
    }

    // ---- 5: 오도미터 체인 대사 ----
    private fun reconcile(km: Float?) {
        if (km == null) return
        val last = prefs.getFloat("last_odo", -1f)
        if (last >= 0 && km - last > 0.05f) {
            ProbeLog.i("CHAIN", "gap detected: last=$last now=$km delta=${km - last} km -> 미확인 구간 생성 대상")
        } else if (last < 0) {
            ProbeLog.i("CHAIN", "first snapshot = $km km")
        }
        prefs.edit().putFloat("last_odo", km).putLong("last_odo_at", System.currentTimeMillis()).apply()
    }

    // ---- 2, 3: AAOS Car API ----
    private fun startCar() {
        val c = runCatching { Car.createCar(context) }.getOrElse { ProbeLog.i("CAR", "createCar failed: $it"); return }
        car = c

        runCatching {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            val cur = ux.currentCarUxRestrictions
            ProbeLog.i("UX", "current requiresDO=${cur.isRequiresDistractionOptimization} active=${cur.activeRestrictions}")
            ux.registerListener { r ->
                ProbeLog.i("UX", "changed requiresDO=${r.isRequiresDistractionOptimization} active=${r.activeRestrictions}")
            }
        }.onFailure { ProbeLog.i("UX", "error: $it") }

        val cpm = runCatching { c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }
            .getOrElse { ProbeLog.i("PROP", "manager error: $it"); return }
        val props = listOf(
            VehiclePropertyIds.GEAR_SELECTION to "GEAR_SELECTION",
            VehiclePropertyIds.IGNITION_STATE to "IGNITION_STATE",
            VehiclePropertyIds.PARKING_BRAKE_ON to "PARKING_BRAKE_ON",
            VehiclePropertyIds.PERF_VEHICLE_SPEED to "PERF_VEHICLE_SPEED",
            VehiclePropertyIds.PERF_ODOMETER to "PERF_ODOMETER",
        )
        for ((id, name) in props) {
            runCatching {
                val v: CarPropertyValue<Any> = cpm.getProperty(id, 0)
                ProbeLog.i("PROP", "$name = ${v.value} (status=${v.status})")
            }.onFailure { ProbeLog.i("PROP", "$name get failed: $it") }
            runCatching {
                val ok = cpm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                    override fun onChangeEvent(value: CarPropertyValue<*>) = ProbeLog.i("PROP", "$name changed = ${value.value}")
                    override fun onErrorEvent(propId: Int, zone: Int) = ProbeLog.i("PROP", "$name error zone=$zone")
                }, id, if (name.startsWith("PERF")) 1f else CarPropertyManager.SENSOR_RATE_ONCHANGE)
                ProbeLog.i("PROP", "$name registerCallback = $ok")
            }.onFailure { ProbeLog.i("PROP", "$name register failed: $it") }
        }
    }

    // ---- 6, 7: NaviHelper ----
    private fun startNavi() {
        val n = NaviHelper(context.applicationContext).also { navi = it }
        n.initialize(
            {
                ProbeLog.i("NAVI", "connected")
                n.addListener(naviListener)
                queryNavi()
                Unit
            },
            { t -> ProbeLog.i("NAVI", "connect failed: $t"); Unit },
        )
    }

    fun queryNavi() {
        val n = navi ?: return
        runCatching { n.getCurrentLocationInfo() }.onFailure { ProbeLog.i("NAVI", "getCurrentLocationInfo error: $it") }
        runCatching { n.getBookmarkInfo() }.onFailure { ProbeLog.i("NAVI", "getBookmarkInfo error: $it") }
        runCatching { n.getRouteStateInfo() }.onFailure { ProbeLog.i("NAVI", "getRouteStateInfo error: $it") }
        ProbeLog.i("NAVI", "queries sent")
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = with(currentLocationInfo) {
            ProbeLog.i("NAVI", "location lat=$latitude lng=$longitude heading=$heading address='$address' routeId=$routeId")
        }
        override fun onBookmarkInfo(bookmarkInfo: List<BookmarkInfo>) {
            ProbeLog.i("NAVI", "bookmarks n=${bookmarkInfo.size}")
            bookmarkInfo.forEach { ProbeLog.i("NAVI", "  bookmark ${it.category} '${it.name}' ${it.address} (${it.latitude},${it.longitude})") }
        }
        override fun onNavigationStatus(navigationStatus: NavigationStatus) = ProbeLog.i("NAVI", "status=$navigationStatus")
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = ProbeLog.i("NAVI", "route started: $routeStartInfo")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = with(destinationArrivedInfo) {
            ProbeLog.i("NAVI", "arrived name='$name' address='$address' distance=$distance duration=$duration")
        }
        override fun onRouteCancelled() = ProbeLog.i("NAVI", "route cancelled")
        override fun onError(errorCode: NaviErrorCode) = ProbeLog.i("NAVI", "error=$errorCode")
    }
}
