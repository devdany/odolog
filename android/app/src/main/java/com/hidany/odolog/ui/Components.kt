package com.hidany.odolog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hidany.odolog.data.Purpose
import com.hidany.odolog.data.TripEntity
import com.hidany.odolog.data.TripSource

@Composable
fun Card(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = C.surface,
        border = border?.let { BorderStroke(2.dp, it) },
    ) { Box(Modifier.padding(24.dp)) { content() } }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = C.sub, modifier = modifier.padding(bottom = 12.dp))

@Composable
fun PurposeChip(p: Purpose) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(purposeColor(p).copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(purposeColor(p)))
        Spacer(Modifier.width(8.dp))
        Text(p.label, style = MaterialTheme.typography.labelMedium, color = purposeColor(p), fontWeight = FontWeight.SemiBold)
    }
}

/** 출퇴근/업무/개인 3버튼. 추천 용도는 채워진 버튼으로 강조. 최소 터치 높이 76dp. */
@Composable
fun PurposeButtons(suggested: Purpose?, selected: Purpose? = null, onPick: (Purpose) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Purpose.COMMUTE, Purpose.BUSINESS, Purpose.PERSONAL).forEach { p ->
            val strong = (selected ?: suggested) == p
            val color = purposeColor(p)
            val mod = Modifier.weight(1f).height(76.dp)
            if (strong) {
                Button(onClick = { onPick(p) }, modifier = mod, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black)) {
                    Text(p.label, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                OutlinedButton(onClick = { onPick(p) }, modifier = mod, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, color.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
                    Text(p.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 도착 카드 / 미확인 구간 카드 (S3, S5). */
@Composable
fun AttentionCard(trip: TripEntity, onPick: (Purpose) -> Unit, onOpen: () -> Unit) {
    val gap = trip.source == TripSource.GAP
    Card(border = if (gap) C.warn else C.accent) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (gap) "기록되지 않은 ${fmtKm(trip.distanceKm)} km" else "도착 · ${fmtKm(trip.distanceKm)} km",
                    style = MaterialTheme.typography.headlineSmall, color = C.text, modifier = Modifier.weight(1f),
                )
                Text("${fmtDate(trip.startAt)} ${fmtTime(trip.startAt)}–${trip.endAt?.let { fmtTime(it) } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium, color = C.sub)
            }
            Spacer(Modifier.height(8.dp))
            val route = if (gap) "앱이 기록하지 못한 주행입니다. 계기판 ${fmtKm(trip.startOdoKm)} → ${fmtKm(trip.endOdoKm ?: trip.startOdoKm)} km"
            else listOfNotNull(trip.startAddr, trip.destName ?: trip.endAddr).joinToString("  →  ").ifBlank { "위치 정보 없음" }
            Text(route, style = MaterialTheme.typography.bodyLarge, color = C.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            trip.suggestedPurpose?.let {
                Spacer(Modifier.height(4.dp))
                Text("추천: ${it.label}", style = MaterialTheme.typography.bodyMedium, color = purposeColor(it))
            }
            Spacer(Modifier.height(18.dp))
            PurposeButtons(suggested = trip.suggestedPurpose, onPick = onPick)
            Spacer(Modifier.height(8.dp))
            Text("운전자·메모 수정", style = MaterialTheme.typography.bodyMedium, color = C.accent,
                modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 8.dp))
        }
    }
}

@Composable
fun TripRow(trip: TripEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(120.dp)) {
            Text(fmtTime(trip.startAt), style = MaterialTheme.typography.titleMedium, color = C.text)
            Text(trip.endAt?.let { "~ ${fmtTime(it)}" } ?: "주행 중", style = MaterialTheme.typography.labelMedium, color = C.sub)
        }
        Column(Modifier.weight(1f)) {
            val route = listOfNotNull(trip.startAddr, trip.destName ?: trip.endAddr).joinToString(" → ")
            Text(
                when {
                    trip.source == TripSource.GAP -> "미확인 구간"
                    route.isNotBlank() -> route
                    trip.purpose == Purpose.PERSONAL -> "개인 운행 (위치 미저장)"
                    else -> "위치 정보 없음"
                },
                style = MaterialTheme.typography.bodyLarge, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(trip.driverName ?: "운전자 미지정", style = MaterialTheme.typography.labelMedium, color = C.sub)
        }
        Text("${fmtKm(trip.distanceKm)} km", style = MaterialTheme.typography.titleMedium, color = C.text,
            modifier = Modifier.padding(horizontal = 16.dp))
        PurposeChip(trip.purpose)
    }
}

@Composable
fun Stat(label: String, value: String, unit: String = "", modifier: Modifier = Modifier, color: Color = C.text) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = C.sub)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
            if (unit.isNotEmpty()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = C.sub, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
