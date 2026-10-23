package com.hidany.odolog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hidany.odolog.Graph
import com.hidany.odolog.data.DriverEntity
import com.hidany.odolog.data.Purpose
import com.hidany.odolog.data.TripEntity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/** 월 요약: 운행기록부 하단 합계와 같은 정의 (업무용 사용거리 = 출퇴근 + 일반업무). */
data class MonthSummary(val totalKm: Double, val businessKm: Double, val commuteKm: Double, val trips: Int, val pending: Int) {
    val ratio: Double get() = if (totalKm > 0) (businessKm + commuteKm) / totalKm else 0.0
    companion object {
        fun of(list: List<TripEntity>): MonthSummary {
            val done = list.filter { !it.isOpen }
            return MonthSummary(
                totalKm = done.sumOf { it.distanceKm },
                businessKm = done.filter { it.purpose == Purpose.BUSINESS }.sumOf { it.distanceKm },
                commuteKm = done.filter { it.purpose == Purpose.COMMUTE }.sumOf { it.distanceKm },
                trips = done.size,
                pending = done.count { it.needsAttention },
            )
        }
    }
}

fun monthRange(ym: LocalDate): Pair<Long, Long> {
    val z = ZoneId.systemDefault()
    val start = ym.withDayOfMonth(1).atStartOfDay(z).toInstant().toEpochMilli()
    val end = ym.withDayOfMonth(1).plusMonths(1).atStartOfDay(z).toInstant().toEpochMilli()
    return start to end
}

@Composable
fun HomeScreen(onOpenTrip: (String) -> Unit) {
    val live by Graph.live.state.collectAsStateWithLifecycle()
    val pending by Graph.db.trips().observePending().collectAsStateWithLifecycle(emptyList())
    val recent by Graph.db.trips().observeRecent(4).collectAsStateWithLifecycle(emptyList())
    val (from, to) = remember { monthRange(LocalDate.now()) }
    val month by Graph.db.trips().observeRange(from, to).collectAsStateWithLifecycle(emptyList())
    val summary = MonthSummary.of(month)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { StatusCard(live.odometerKm, live.moving, live.openTripId != null, live.stoppingUntil, live.serviceRunning) }
        item { DriverCard() }
        val suggested = pending.filter { it.suggestedPurpose != null }
        if (suggested.size >= 2) item {
            Card(border = C.accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("확인할 운행 ${pending.size}건", style = MaterialTheme.typography.titleLarge, color = C.text)
                        Text("추천 용도가 맞으면 한 번에 확정하세요. 다른 운행은 아래에서 하나씩 고를 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium, color = C.sub)
                    }
                    androidx.compose.material3.Button(
                        onClick = { suggested.forEach { Graph.actions.confirmPurpose(it.id, it.suggestedPurpose!!) } },
                        modifier = Modifier.height(72.dp),
                    ) { Text("추천대로 모두 확정 (${suggested.size}건)", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        items(pending, key = { it.id }) { t ->
            AttentionCard(t, onPick = { Graph.actions.confirmPurpose(t.id, it) }, onOpen = { onOpenTrip(t.id) })
        }
        item {
            Card {
                Column {
                    SectionTitle("${LocalDate.now().monthValue}월 운행기록부")
                    Row(Modifier.fillMaxWidth()) {
                        Stat("총 주행", fmtKm(summary.totalKm), "km", Modifier.weight(1f))
                        Stat("업무용 사용거리", fmtKm(summary.businessKm + summary.commuteKm), "km", Modifier.weight(1f))
                        Stat("업무사용비율", "%.1f".format(summary.ratio * 100), "%", Modifier.weight(1f), C.accent)
                        Stat("미확정", "${summary.pending}", "건", Modifier.weight(1f), if (summary.pending > 0) C.warn else C.text)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) item {
            Card {
                Column {
                    SectionTitle("최근 운행")
                    recent.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = C.line)
                        TripRow(t) { onOpenTrip(t.id) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusCard(odo: Double?, moving: Boolean?, recording: Boolean, stoppingUntil: Long?, serviceRunning: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stoppingUntil) { while (stoppingUntil != null) { now = System.currentTimeMillis(); delay(1000) } }
    val (label, color) = when {
        !serviceRunning -> "기록 서비스 시작 중" to C.sub
        moving == true -> "주행 중 · 계기판 거리 기록 중" to C.accent
        recording && stoppingUntil != null -> "정차 · ${((stoppingUntil - now) / 1000).coerceAtLeast(0)}초 후 운행 종료" to C.warn
        else -> "주차 · 시동 후 자동 기록" to C.sub
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("오도로그", style = MaterialTheme.typography.labelMedium, color = C.sub)
                Text(label, style = MaterialTheme.typography.titleLarge, color = color)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("계기판", style = MaterialTheme.typography.labelMedium, color = C.sub)
                Text(odo?.let { "${fmtKm(it)} km" } ?: "—", style = MaterialTheme.typography.displaySmall, color = C.text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriverCard() {
    val drivers by Graph.db.drivers().observeAll().collectAsStateWithLifecycle(emptyList())
    val currentId by Graph.prefs.currentDriverIdFlow.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val current = drivers.firstOrNull { it.id == currentId }
    val paired by Graph.prefs.orgNameFlow.collectAsStateWithLifecycle()
    val syncing by Graph.sync.syncing.collectAsStateWithLifecycle()
    LaunchedEffect(expanded) { if (expanded && paired != null) Graph.sync.requestSync(immediate = true) }
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("오늘 운전자", style = MaterialTheme.typography.labelMedium, color = C.sub)
                    Text(current?.let { listOfNotNull(it.name, it.dept).joinToString(" · ") } ?: "운전자를 선택해 주세요",
                        style = MaterialTheme.typography.titleLarge, color = if (current != null) C.text else C.warn)
                }
                Text(if (expanded) "닫기" else "변경", style = MaterialTheme.typography.labelLarge, color = C.accent,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp))
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                if (paired != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (syncing) "관리자 웹에서 운전자 목록을 받는 중…" else "관리자 웹에 등록된 운전자", color = C.sub,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("새로고침", style = MaterialTheme.typography.labelLarge, color = C.accent,
                        modifier = Modifier.clickable { Graph.sync.requestSync(immediate = true) }.padding(12.dp))
                }
                if (drivers.isEmpty()) Text(if (paired != null) "관리자 웹 > 운전자에서 추가하세요" else "설정 > 운전자에서 운전자를 추가하세요",
                    color = C.sub, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    drivers.forEach { d: DriverEntity ->
                        FilterChip(
                            selected = d.id == currentId,
                            onClick = { Graph.actions.selectCurrentDriver(d); expanded = false },
                            label = { Text(d.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.accent, selectedLabelColor = androidx.compose.ui.graphics.Color.Black),
                        )
                    }
                }
            }
        }
    }
}
