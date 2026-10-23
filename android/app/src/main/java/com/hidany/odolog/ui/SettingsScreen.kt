package com.hidany.odolog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hidany.odolog.BuildConfig
import com.hidany.odolog.Graph
import com.hidany.odolog.data.PlaceKind
import com.hidany.odolog.record.TripRecorderService
import kotlinx.coroutines.launch

/** S6 설정: 관리자 웹 연결, 운전자, 장소, 기록 설정. */
@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("설정", style = MaterialTheme.typography.headlineSmall, color = C.text)
        PairingCard()
        DriversCard()
        PlacesCard()
        RecordingCard()
        Card {
            Column {
                SectionTitle("개인정보")
                Text("오도로그는 운행기록부 작성에 필요한 계기판 거리·시각·운전자·용도만 기록합니다. " +
                    "개인 용도로 확정한 운행은 출발·도착 위치를 삭제하고 거리만 남기며, 실시간 위치 추적은 하지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium, color = C.sub)
                Spacer(Modifier.height(8.dp))
                Text("버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = C.sub)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PairingCard() {
    val org by Graph.prefs.orgNameFlow.collectAsStateWithLifecycle()
    val vehicle by Graph.prefs.vehicleLabelFlow.collectAsStateWithLifecycle()
    val lastSync by Graph.prefs.lastSyncAtFlow.collectAsStateWithLifecycle()
    val syncError by Graph.sync.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Card {
        Column {
            SectionTitle("관리자 웹 연결")
            if (org != null) {
                Text("$org", style = MaterialTheme.typography.titleLarge, color = C.text)
                vehicle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = C.sub) }
                Text(if (lastSync > 0) "마지막 동기화 ${fmtDateTime(lastSync)}" else "동기화 대기",
                    style = MaterialTheme.typography.bodyMedium, color = C.sub)
                syncError?.let { Text(it, color = C.warn, style = MaterialTheme.typography.bodyMedium) }
                Row {
                    TextButton(onClick = { Graph.sync.requestSync(immediate = true) }) { Text("지금 동기화") }
                    TextButton(onClick = { Graph.sync.unpair() }) { Text("연결 해제", color = C.warn) }
                }
            } else {
                Text("관리자 웹에서 차량을 등록하고 발급된 6자리 코드를 입력하세요. 연결하지 않아도 기록은 차량에 저장됩니다.",
                    style = MaterialTheme.typography.bodyMedium, color = C.sub)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("페어링 코드") }, singleLine = true, textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }), modifier = Modifier.width(260.dp))
                    Spacer(Modifier.width(16.dp))
                    Button(enabled = code.length == 6 && !busy, modifier = Modifier.height(64.dp), onClick = {
                        busy = true
                        scope.launch {
                            msg = Graph.sync.pair(code).fold({ "연결됨: $it" }, { "연결 실패: ${it.message}" })
                            busy = false
                        }
                    }) { Text(if (busy) "연결 중…" else "연결") }
                }
                msg?.let { Text(it, color = C.sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                if (!Graph.sync.configured) Text("서버 주소가 설정되지 않은 빌드입니다", color = C.warn, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DriversCard() {
    val drivers by Graph.db.drivers().observeAll().collectAsStateWithLifecycle(emptyList())
    val paired = Graph.prefs.orgNameFlow.collectAsStateWithLifecycle().value != null
    var name by remember { mutableStateOf("") }
    var dept by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val add = { if (name.isNotBlank()) { Graph.actions.addLocalDriver(name, dept); name = ""; dept = "" }; focus.clearFocus(); keyboard?.hide() }
    Card {
        Column {
            SectionTitle("운전자")
            if (paired) Text("관리자 웹에 등록된 운전자 목록이 자동으로 동기화됩니다.", color = C.sub, style = MaterialTheme.typography.bodyMedium)
            drivers.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(listOfNotNull(d.name, d.dept).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = C.text, modifier = Modifier.weight(1f))
                    if (!d.fromServer) TextButton(onClick = { Graph.actions.removeDriver(d.id) }) { Text("삭제", color = C.sub) }
                }
                HorizontalDivider(color = C.line)
            }
            if (!paired) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(dept, { dept = it }, label = { Text("부서") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
                    Spacer(Modifier.width(12.dp))
                    Button(enabled = name.isNotBlank(), modifier = Modifier.height(64.dp), onClick = { add() }) { Text("추가") }
                }
            }
        }
    }
}

@Composable
private fun PlacesCard() {
    val places by Graph.db.places().observeAll().collectAsStateWithLifecycle(emptyList())
    val locate: suspend () -> com.hidany.odolog.record.GeoPoint? = { TripRecorderService.instance?.locator()?.currentLocation() }
    Card {
        Column {
            SectionTitle("장소 (용도 자동 추천)")
            Text("집·회사를 등록하면 출퇴근을, 업무로 확정한 장소는 다음부터 업무를 자동으로 추천합니다. 내비게이션 즐겨찾기의 집·회사도 사용합니다.",
                style = MaterialTheme.typography.bodyMedium, color = C.sub)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { Graph.actions.setPlaceHere(PlaceKind.HOME, locate) }, modifier = Modifier.height(60.dp)) { Text("현재 위치를 집으로") }
                OutlinedButton(onClick = { Graph.actions.setPlaceHere(PlaceKind.OFFICE, locate) }, modifier = Modifier.height(60.dp)) { Text("현재 위치를 회사로") }
            }
            Spacer(Modifier.height(8.dp))
            places.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (p.kind) { PlaceKind.HOME -> "집"; PlaceKind.OFFICE -> "회사"; PlaceKind.BUSINESS -> "업무" },
                        color = C.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                    Text(p.label, color = C.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { Graph.actions.removePlace(p.id) }) { Text("삭제", color = C.sub) }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard() {
    val grace by Graph.prefs.stopGraceSecFlow.collectAsStateWithLifecycle()
    Card {
        Column {
            SectionTitle("기록")
            Text("P단으로 바꾼 뒤 이 시간이 지나면 운행이 끝납니다. 그 전에 다시 출발하면 같은 운행으로 이어집니다.",
                style = MaterialTheme.typography.bodyMedium, color = C.sub)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(300 to "5분 (기본)", 120 to "2분", 15 to "15초 (시연)").forEach { (sec, label) ->
                    if (grace == sec) Button(onClick = {}, modifier = Modifier.height(60.dp)) { Text(label) }
                    else OutlinedButton(onClick = { Graph.prefs.stopGraceSec = sec }, modifier = Modifier.height(60.dp)) { Text(label) }
                }
            }
        }
    }
}
