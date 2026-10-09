package ua.lujek.monetizekit.sample

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class SampleColors(
    val canvas: Color,
    val panel: Color,
    val primary: Color,
    val disabledControl: Color,
    val primaryText: Color,
    val title: Color,
    val body: Color,
    val outline: Color,
)

@Immutable
data class SampleTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

@Immutable
data class SampleSpacing(
    val screen: Dp,
    val section: Dp,
    val item: Dp,
    val card: Dp,
    val border: Dp,
    val buttonVertical: Dp,
)

@Immutable
data class SampleShapes(
    val card: RoundedCornerShape,
    val button: RoundedCornerShape,
)

val LocalSampleColors = staticCompositionLocalOf<SampleColors> {
    error("MonetizeKitSampleTheme is required")
}
val LocalSampleTypography = staticCompositionLocalOf<SampleTypography> {
    error("MonetizeKitSampleTheme is required")
}
val LocalSampleSpacing = staticCompositionLocalOf<SampleSpacing> {
    error("MonetizeKitSampleTheme is required")
}
val LocalSampleShapes = staticCompositionLocalOf<SampleShapes> {
    error("MonetizeKitSampleTheme is required")
}

@Composable
fun MonetizeKitSampleTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSampleColors provides SampleColors(
            canvas = Color(0xFFF5F7FB),
            panel = Color.White,
            primary = Color(0xFF155EEF),
            disabledControl = Color(0xFFD0D5DD),
            primaryText = Color.White,
            title = Color(0xFF101828),
            body = Color(0xFF475467),
            outline = Color(0xFFD0D5DD),
        ),
        LocalSampleTypography provides SampleTypography(
            title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            subtitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
            caption = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
            button = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        ),
        LocalSampleSpacing provides SampleSpacing(
            screen = 20.dp,
            section = 24.dp,
            item = 12.dp,
            card = 16.dp,
            border = 1.dp,
            buttonVertical = 14.dp,
        ),
        LocalSampleShapes provides SampleShapes(
            card = RoundedCornerShape(18.dp),
            button = RoundedCornerShape(12.dp),
        ),
        content = content,
    )
}
