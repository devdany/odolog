package com.hidany.odolog.sync

import android.content.Context
import android.util.Log
import com.hidany.odolog.BuildConfig
import com.hidany.odolog.Graph
import com.hidany.odolog.data.DriverEntity
import com.hidany.odolog.data.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 관리자 웹(Supabase) 연동. 차량 앱은 anon key + device token으로 security definer RPC만 호출한다.
 * 오프라인 우선: 페어링 전이거나 네트워크가 없어도 로컬 기록은 그대로, 연결되면 미전송분을 멱등 upsert.
 */
class SyncClient(private val context: Context, private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val trigger = Channel<Boolean>(Channel.CONFLATED)
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val configured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    init {
        scope.launch {
            for (immediate in trigger) {
                if (!immediate) delay(1500) // 짧은 시간 내 여러 변경을 한 번에
                _syncing.value = true
                runCatching { syncNow() }.onFailure {
                    Log.w(TAG, "sync failed", it); _status.value = "동기화 실패: ${it.message}"
                }
                _syncing.value = false
            }
        }
    }

    fun requestSync(immediate: Boolean = false) { trigger.trySend(immediate) }

    /** 관리자 웹에서 발급한 6자리 코드로 차량 연결. */
    suspend fun pair(code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            check(configured) { "서버 설정이 없습니다" }
            val res = rpc("pair_vehicle", buildJsonObject { put("p_code", code.trim()) }).jsonObject
            val prefs = Graph.prefs
            prefs.deviceToken = res["device_token"]!!.jsonPrimitive.content
            val plate = res["plate_no"]?.jsonPrimitive?.content
            val model = res["model"]?.jsonPrimitive?.contentOrNullSafe()
            prefs.vehicleLabel = listOfNotNull(model, plate).joinToString(" · ")
            prefs.orgName = res["org_name"]?.jsonPrimitive?.content
            syncNow()
            prefs.orgName ?: "연결됨"
        }
    }

    sealed interface PinResult {
        data class Ok(val checkinId: String) : PinResult
        data class Wrong(val remaining: Int) : PinResult
        data object Locked : PinResult
        data object NoPin : PinResult
        data class Error(val message: String) : PinResult
    }

    /** 운전자 PIN 확인 → 서버 체크인 id. 네트워크가 필요하다. */
    suspend fun verifyPin(driverId: String, pin: String): PinResult = withContext(Dispatchers.IO) {
        val token = Graph.prefs.deviceToken ?: return@withContext PinResult.Error("관리자 웹에 연결되지 않았습니다")
        runCatching {
            val r = rpc("verify_driver_pin", buildJsonObject { put("p_token", token); put("p_driver", driverId); put("p_pin", pin) }).jsonObject
            if (r["ok"]?.jsonPrimitive?.content == "true") PinResult.Ok(r["checkin_id"]!!.jsonPrimitive.content)
            else when (r["reason"]?.jsonPrimitive?.content) {
                "wrong_pin" -> PinResult.Wrong(r["remaining"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                "locked" -> PinResult.Locked
                "no_pin" -> PinResult.NoPin
                else -> PinResult.Error("확인할 수 없는 운전자입니다")
            }
        }.getOrElse { PinResult.Error("네트워크 연결을 확인해 주세요") }
    }

    fun unpair() = scope.launch {
        Graph.prefs.deviceToken = null; Graph.prefs.vehicleLabel = null; Graph.prefs.orgName = null
        Graph.prefs.currentDriverId = null; Graph.prefs.checkinId = null
        Graph.db.drivers().clearServer()
    }

    private suspend fun syncNow() = withContext(Dispatchers.IO) {
        val token = Graph.prefs.deviceToken ?: return@withContext
        if (!configured) return@withContext
        val pending = Graph.db.trips().unsynced()
        val at = System.currentTimeMillis()
        val body = buildJsonObject {
            put("p_token", token)
            put("p_trips", buildJsonArray { pending.forEach { add(it.toJson()) } })
            Graph.live.state.value.odometerKm?.let { put("p_odo_km", it) }
        }
        val res = rpc("vehicle_sync", body).jsonObject
        if (pending.isNotEmpty()) Graph.db.trips().markSynced(pending.map { it.id }, at)
        val drivers = res["drivers"]?.jsonArray.orEmpty().map { d ->
            val o = d.jsonObject
            DriverEntity(
                id = o["id"]!!.jsonPrimitive.content,
                name = o["name"]!!.jsonPrimitive.content,
                dept = o["dept"]?.jsonPrimitive?.contentOrNullSafe(),
                fromServer = true,
                hasPin = o["has_pin"]?.jsonPrimitive?.content == "true",
            )
        }
        Graph.db.drivers().clearServer()
        Graph.db.drivers().insertAll(drivers)
        Graph.prefs.lastSyncAt = at
        _status.value = null
        Log.i(TAG, "synced ${pending.size} trips, ${drivers.size} drivers")
    }

    private fun rpc(fn: String, body: JsonObject): kotlinx.serialization.json.JsonElement {
        val req = Request.Builder()
            .url("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/$fn")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val msg = runCatching { json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                error(msg ?: "HTTP ${r.code}")
            }
            return json.parseToJsonElement(text.ifBlank { "null" })
        }
    }

    private fun TripEntity.toJson() = buildJsonObject {
        put("local_id", id)
        // 서버 운전자(uuid)만 매칭, 로컬 운전자는 이름으로 보존
        driverId?.takeIf { !it.startsWith("local-") }?.let { put("driver_id", it) }
        driverName?.let { put("driver_name", it) }
        put("start_at", Instant.ofEpochMilli(startAt).toString())
        endAt?.let { put("end_at", Instant.ofEpochMilli(it).toString()) }
        put("start_odo_km", startOdoKm)
        endOdoKm?.let { put("end_odo_km", it) }
        put("purpose", purpose.name)
        startAddr?.let { put("start_addr", it) }
        endAddr?.let { put("end_addr", it) }
        destName?.let { put("dest_name", it) }
        tollWon?.let { put("toll_won", it) }
        note?.let { put("note", it) }
        put("source", source.name)
        confirmedAt?.let { put("confirmed_at", Instant.ofEpochMilli(it).toString()) }
        checkinId?.let { put("checkin_id", it) }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (this.toString() == "null") null else content
    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())

    companion object { private const val TAG = "OdoLog.Sync" }
}
