package com.hmgcontest.probe

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** logcat(tag PROBE) + 파일 + 화면 표시용 로그. `adb logcat -s PROBE`로 확인. */
object ProbeLog {
    private const val TAG = "PROBE"
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val lines = CopyOnWriteArrayList<String>()
    private var file: File? = null
    var onLine: ((String) -> Unit)? = null

    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "probe.log")
    }

    fun i(area: String, msg: String) {
        val line = "${fmt.format(Date())} [$area] $msg"
        Log.i(TAG, line)
        lines.add(line)
        runCatching { file?.appendText(line + "\n") }
        onLine?.invoke(line)
    }

    fun all(): List<String> = lines.toList()
    fun clear() = lines.clear()
}
