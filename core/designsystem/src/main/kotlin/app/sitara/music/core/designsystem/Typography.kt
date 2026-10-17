package app.sitara.music.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold),
)

val LyricStyle = TextStyle(fontFamily = InterDisplay, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
val SectionStyle = TextStyle(fontFamily = InterDisplay, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)

val SitaraTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterDisplay, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = InterDisplay, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = InterDisplay, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = InterDisplay, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = InterDisplay, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = InterDisplay, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = InterDisplay, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)
