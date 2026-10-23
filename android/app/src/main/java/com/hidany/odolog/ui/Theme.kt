package com.hidany.odolog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hidany.odolog.data.Purpose
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object C {
    val bg = Color(0xFF0E1116)
    val surface = Color(0xFF171B22)
    val surface2 = Color(0xFF1F252E)
    val line = Color(0xFF2A313C)
    val text = Color(0xFFE8ECF1)
    val sub = Color(0xFF9AA4B2)
    val accent = Color(0xFF14B8A6)
    val warn = Color(0xFFF59E0B)
    val commute = Color(0xFF60A5FA)
    val business = Color(0xFF14B8A6)
    val personal = Color(0xFFA78BFA)
    val pending = Color(0xFFF59E0B)
}

fun purposeColor(p: Purpose) = when (p) {
    Purpose.COMMUTE -> C.commute
    Purpose.BUSINESS -> C.business
    Purpose.PERSONAL -> C.personal
    Purpose.UNCONFIRMED -> C.pending
}

// 차량 화면은 시청 거리가 멀어 모바일보다 한 단계 큰 글자를 쓴다
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 16.sp),
)

@Composable
fun OdoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = Color.Black, background = C.bg, surface = C.surface,
            onSurface = C.text, onBackground = C.text, surfaceVariant = C.surface2, outline = C.line,
        ),
        typography = typography,
        content = content,
    )
}

private val kmFmt = DecimalFormat("#,##0.0")
private val zone: ZoneId = ZoneId.systemDefault()
fun fmtKm(km: Double): String = kmFmt.format(km)
fun fmtTime(ms: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtDate(ms: Long): String = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).format(Instant.ofEpochMilli(ms).atZone(zone))
fun fmtDateTime(ms: Long): String = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").format(Instant.ofEpochMilli(ms).atZone(zone))
