package com.hidany.odolog.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hidany.odolog.Graph
import com.hidany.odolog.data.Purpose
import com.hidany.odolog.data.TripSource

/** 운행 상세: 운행기록부 한 행의 모든 칸 + 수정. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripDetail(tripId: String, onClose: () -> Unit) {
    val trip by remember(tripId) { Graph.db.trips().observe(tripId) }.collectAsStateWithLifecycle(null)
    val drivers by Graph.db.drivers().observeAll().collectAsStateWithLifecycle(emptyList())
    val t = trip ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row {
            Text(if (t.source == TripSource.GAP) "미확인 구간" else "운행 상세", style = MaterialTheme.typography.headlineSmall,
                color = C.text, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기", tint = C.text) }
        }
        Spacer(Modifier.height(16.dp))
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("사용일시", "${fmtDateTime(t.startAt)} ~ ${t.endAt?.let { fmtTime(it) } ?: "주행 중"}")
                Row {
                    Field("주행 전 계기판", "${fmtKm(t.startOdoKm)} km", Modifier.weight(1f))
                    Field("주행 후 계기판", t.endOdoKm?.let { "${fmtKm(it)} km" } ?: "—", Modifier.weight(1f))
                    Field("주행거리", "${fmtKm(t.distanceKm)} km", Modifier.weight(1f))
                }
                Field("출발", t.startAddr ?: "—")
                Field("도착", listOfNotNull(t.destName, t.endAddr).joinToString(" · ").ifBlank { "—" })
                t.tollWon?.let { Field("통행료", "%,d원".format(it)) }
                if (t.purpose == Purpose.PERSONAL) Text("개인 용도 운행은 위치를 저장하지 않습니다.", color = C.personal, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card {
            Column {
                SectionTitle("용도")
                PurposeButtons(suggested = t.suggestedPurpose, selected = t.purpose.takeIf { it != Purpose.UNCONFIRMED }) {
                    Graph.actions.confirmPurpose(t.id, it)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Card {
            Column {
                SectionTitle("운전자")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    drivers.forEach { d ->
                        FilterChip(
                            selected = d.id == t.driverId,
                            onClick = { Graph.actions.setDriver(t.id, d) },
                            label = { Text(d.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(10.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.accent, selectedLabelColor = Color.Black),
                        )
                    }
                }
                if (drivers.isEmpty()) Text(t.driverName ?: "운전자 미지정", color = C.sub)
            }
        }
        Spacer(Modifier.height(20.dp))
        Card {
            Column {
                SectionTitle("비고 (방문처·목적)")
                var note by remember(t.id) { mutableStateOf(t.note ?: "") }
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                val save = { Graph.actions.updateNote(t.id, note); focus.clearFocus(); keyboard?.hide() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.weight(1f),
                        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, placeholder = { Text("예: ○○상사 납품 미팅") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }))
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { save() }, enabled = note != (t.note ?: ""), modifier = Modifier.height(60.dp)) {
                        Text("저장", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (t.note != null && note == t.note) Text("저장됨", color = C.sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = C.sub)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = C.text)
    }
}
