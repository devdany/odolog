package com.hidany.odolog.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hidany.odolog.Graph
import com.hidany.odolog.record.TripRecorderService

class MainActivity : ComponentActivity() {
    private val openTrip = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TripRecorderService.start(this)
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        openTrip.value = intent.getStringExtra(EXTRA_TRIP)
        setContent { OdoLogTheme { AppRoot(openTrip.value) { openTrip.value = it } } }
    }

    override fun onResume() {
        super.onResume()
        // 관리자 웹에서 바뀐 운전자 목록 등을 화면을 열 때마다 받아온다
        Graph.sync.requestSync(immediate = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TRIP)?.let { openTrip.value = it }
    }

    companion object { const val EXTRA_TRIP = "trip_id" }
}

private enum class Tab(val label: String) { HOME("홈"), LOG("운행일지"), SETTINGS("설정") }

@Composable
private fun AppRoot(openTrip: String?, setOpenTrip: (String?) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val pending by Graph.db.trips().observePending().collectAsStateWithLifecycle(emptyList())

    Row(Modifier.fillMaxSize().background(C.bg)) {
        NavigationRail(containerColor = C.surface, modifier = Modifier.fillMaxHeight()) {
            Tab.entries.forEach { t ->
                NavigationRailItem(
                    selected = tab == t && openTrip == null,
                    onClick = { tab = t; setOpenTrip(null) },
                    icon = {
                        val icon = when (t) { Tab.HOME -> Icons.Default.Home; Tab.LOG -> Icons.AutoMirrored.Filled.MenuBook; Tab.SETTINGS -> Icons.Default.Settings }
                        if (t == Tab.HOME && pending.isNotEmpty()) BadgedBox(badge = { Badge { Text("${pending.size}") } }) { Icon(icon, t.label) }
                        else Icon(icon, t.label)
                    },
                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationRailItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = C.accent,
                        unselectedIconColor = C.sub, unselectedTextColor = C.sub, selectedTextColor = C.text),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
            if (openTrip != null) TripDetail(openTrip) { setOpenTrip(null) }
            else when (tab) {
                Tab.HOME -> HomeScreen(onOpenTrip = setOpenTrip)
                Tab.LOG -> LogbookScreen(onOpenTrip = setOpenTrip)
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
