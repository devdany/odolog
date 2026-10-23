package com.hidany.odolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hidany.odolog.Graph
import com.hidany.odolog.data.DriverEntity
import com.hidany.odolog.sync.SyncClient.PinResult
import kotlinx.coroutines.launch

/**
 * 운전자 본인 확인. 차량 화면용 큰 숫자 키패드 (주차 중에만 화면이 보인다).
 * PIN은 서버에서만 검증하고, 성공 시 받은 체크인 id를 운행에 붙인다.
 */
@Composable
fun PinDialog(driver: DriverEntity, onDismiss: () -> Unit, onVerified: (checkinId: String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (pin.length < 4 || busy) return
        busy = true; error = null
        scope.launch {
            when (val r = Graph.sync.verifyPin(driver.id, pin)) {
                is PinResult.Ok -> onVerified(r.checkinId)
                is PinResult.Wrong -> error = if (r.remaining > 0) "PIN이 맞지 않습니다 (남은 시도 ${r.remaining}회)" else "PIN이 맞지 않습니다. 5분 뒤 다시 시도하세요"
                PinResult.Locked -> error = "PIN을 여러 번 틀려 잠겼습니다. 5분 뒤 다시 시도하세요"
                PinResult.NoPin -> error = "PIN이 설정되지 않았습니다. 관리자에게 요청하세요"
                is PinResult.Error -> error = r.message
            }
            pin = ""; busy = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = C.surface) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${driver.name}님 맞으신가요?", style = MaterialTheme.typography.headlineSmall, color = C.text)
                Text("본인 확인을 위해 PIN을 입력하세요", style = MaterialTheme.typography.bodyMedium, color = C.sub)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(maxOf(4, pin.length)) { i ->
                        Box(Modifier.size(18.dp).clip(CircleShape).background(if (i < pin.length) C.accent else C.line))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(error ?: if (busy) "확인 중…" else " ", color = if (error != null) C.warn else C.sub,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "지움", "0", "확인")
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        row.forEach { k ->
                            OutlinedButton(
                                onClick = {
                                    when (k) {
                                        "지움" -> pin = pin.dropLast(1)
                                        "확인" -> submit()
                                        else -> if (pin.length < 6) { pin += k; error = null }
                                    }
                                },
                                enabled = !busy && (k != "확인" || pin.length >= 4),
                                modifier = Modifier.width(110.dp).height(76.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = if (k == "확인") ButtonDefaults.outlinedButtonColors(containerColor = C.accent, contentColor = androidx.compose.ui.graphics.Color.Black)
                                else ButtonDefaults.outlinedButtonColors(contentColor = C.text),
                            ) { Text(k, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("취소", style = MaterialTheme.typography.labelLarge, color = C.sub) }
            }
        }
    }
}
