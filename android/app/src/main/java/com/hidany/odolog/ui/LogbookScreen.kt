package com.hidany.odolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hidany.odolog.Graph
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** S4 운행일지: 월별, 일자별 그룹. */
@Composable
fun LogbookScreen(onOpenTrip: (String) -> Unit) {
    var ym by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val range = remember(ym) { monthRange(ym) }
    val paired by Graph.prefs.orgNameFlow.collectAsStateWithLifecycle()
    val driverId by Graph.prefs.currentDriverIdFlow.collectAsStateWithLifecycle()
    // 연결된 차량은 확인된 운전자 본인의 운행만 (전체 운행일지는 관리자 웹에서)
    val trips by remember(range, paired, driverId) {
        when {
            paired == null -> Graph.db.trips().observeRange(range.first, range.second)
            driverId != null -> Graph.db.trips().observeRangeFor(driverId!!, range.first, range.second)
            else -> kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(emptyList())
    val s = MonthSummary.of(trips)
    val byDay = trips.groupBy { Instant.ofEpochMilli(it.startAt).atZone(ZoneId.systemDefault()).toLocalDate() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달", tint = C.text) }
            Text("${ym.year}년 ${ym.monthValue}월" + if (paired != null) " · 내 운행" else "", style = MaterialTheme.typography.headlineSmall, color = C.text)
            IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달", tint = C.text) }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            Row(Modifier.fillMaxWidth()) {
                Stat("총 주행", fmtKm(s.totalKm), "km", Modifier.weight(1f))
                Stat("출퇴근", fmtKm(s.commuteKm), "km", Modifier.weight(1f), C.commute)
                Stat("일반업무", fmtKm(s.businessKm), "km", Modifier.weight(1f), C.business)
                Stat("업무사용비율", "%.1f".format(s.ratio * 100), "%", Modifier.weight(1f), C.accent)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (paired != null && driverId == null) {
            Text("홈에서 운전자를 확인하면 본인의 운행일지를 볼 수 있습니다. 차량 전체 운행일지는 관리자 웹에서 확인합니다.",
                color = C.sub, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
        } else if (trips.isEmpty()) {
            Text("이 달의 운행 기록이 없습니다", color = C.sub, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(byDay.entries.toList(), key = { it.key.toString() }) { (day, list) ->
                Card {
                    Column {
                        Row {
                            Text(fmtDate(list.first().startAt), style = MaterialTheme.typography.titleMedium, color = C.text, modifier = Modifier.weight(1f))
                            Text("${fmtKm(list.sumOf { it.distanceKm })} km", style = MaterialTheme.typography.titleMedium, color = C.sub)
                        }
                        Spacer(Modifier.height(8.dp))
                        list.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(color = C.line)
                            TripRow(t) { onOpenTrip(t.id) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
