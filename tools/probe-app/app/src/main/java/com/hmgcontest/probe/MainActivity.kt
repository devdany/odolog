package com.hmgcontest.probe

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProbeLog.init(this)
        requestPermissions(arrayOf("android.car.permission.CAR_SPEED", "android.permission.POST_NOTIFICATIONS"), 1)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("Start") { ProbeService.start(this@MainActivity, "manual") })
            addView(button("Stop") { stopService(Intent(this@MainActivity, ProbeService::class.java)) })
            addView(button("Navi query") { ProbeService.start(this@MainActivity, "manual", ProbeService.ACTION_NAVI) })
            addView(button("Clear") { ProbeLog.clear(); logView.text = "" })
        }
        logView = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(buttons)
            addView(ScrollView(this@MainActivity).apply { addView(logView) })
        }
        setContentView(root)
        ProbeService.start(this, "activity")
    }

    override fun onResume() {
        super.onResume()
        logView.text = ProbeLog.all().joinToString("\n")
        ProbeLog.onLine = { line -> runOnUiThread { logView.append("\n" + line) } }
    }

    override fun onPause() {
        ProbeLog.onLine = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissions.zip(grantResults.toTypedArray()).forEach { (p, r) -> ProbeLog.i("PERM", "$p granted=${r == 0}") }
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }
}
