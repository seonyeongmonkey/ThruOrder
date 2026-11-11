package ai.thruorder.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Pleos Connect Grayscale 토큰 (basic_00 ~ basic_900)
object Gray {
    val g00 = Color(0xFFFFFFFF); val g50 = Color(0xFFF7F8FA); val g100 = Color(0xFFEDEEF2)
    val g200 = Color(0xFFE0E1E6); val g300 = Color(0xFFC7C9CE); val g400 = Color(0xFFA4A8AE)
    val g500 = Color(0xFF686A72); val g600 = Color(0xFF44464E); val g700 = Color(0xFF313236)
    val g800 = Color(0xFF1D1E21); val g900 = Color(0xFF131417)
}

/**
 * 브랜드 컬러는 하나만 쓴다 (스루오더 블루). 상태 구분은 색이 아니라 아이콘·문구·채움으로 한다.
 * 빨강은 품절·지연 경고에만 쓴다. 앱 아이콘(res/drawable/ic_launcher_*)도 같은 색.
 */
val Brand = Color(0xFF2F6BFF)
val BrandSoftDark = Color(0xFF1C2A4D)
val BrandSoftLight = Color(0xFFE6EDFF)
val Danger = Color(0xFFE5484D)

private val DarkColors = darkColorScheme(
    primary = Brand, onPrimary = Gray.g00,
    primaryContainer = BrandSoftDark, onPrimaryContainer = Color(0xFFB9CBFF),
    background = Gray.g900, onBackground = Gray.g00,
    surface = Gray.g800, onSurface = Gray.g00,
    surfaceVariant = Gray.g700, onSurfaceVariant = Gray.g300,
    outline = Gray.g600, error = Danger,
)

private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Gray.g00,
    primaryContainer = BrandSoftLight, onPrimaryContainer = Color(0xFF1B3FA6),
    background = Gray.g50, onBackground = Gray.g900,
    surface = Gray.g00, onSurface = Gray.g900,
    surfaceVariant = Gray.g100, onSurfaceVariant = Gray.g500,
    outline = Gray.g200, error = Danger,
)

// Pleos Font Hierarchy: Headline 60/56/48, Title 40/36/32, Body 30/28/26 (Asta Sans → 시스템 기본 서체로 대체)
private val PleosType = Typography(
    displayLarge = TextStyle(fontSize = 60.sp, lineHeight = 76.sp, fontWeight = FontWeight.ExtraBold),
    displayMedium = TextStyle(fontSize = 56.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold),
    displaySmall = TextStyle(fontSize = 48.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp),
    bodyMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
    bodySmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp),
    labelLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ThruOrderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PleosType,
        content = content,
    )
}
