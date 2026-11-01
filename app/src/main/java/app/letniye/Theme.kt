package app.letniye

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Токены из CSS: :root (тёмная) и .screen.light (светлая). */
@Immutable
class LColors(
    val light: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val bg: Color,
    val surface: Color,
    val island: Color,
    val text: Color,
    val text2: Color,
    val tint: Color,
    val wave: Color,
    val ga: Color,
    val gb: Color,
    val rimHi: Color,
    val rimLo: Color,
    val gsh: Color,
    val gglow: Color,
)

val LightColors = LColors(
    light = true,
    primary = Color(0xFF74AE2E),
    onPrimary = Color(0xFF10200A),
    bg = Color(0xFFF8F6EC),
    surface = Color(0xFFECE9DB),
    island = Color(0xFFE1DECB),
    text = Color(0xFF1E1F17),
    text2 = Color(0xFF7A7A6B),
    tint = Color(0xFF74AE2E).copy(alpha = .14f),
    wave = Color(0xFF74AE2E).copy(alpha = .34f),
    ga = Color(0xFFE9E8DB),
    gb = Color(0xFFFDFCF7),
    rimHi = Color.White,
    rimLo = Color.White.copy(alpha = .6f),
    gsh = Color(0xFF60623C).copy(alpha = .07f),
    gglow = Color.White.copy(alpha = .8f),
)

val DarkColors = LColors(
    light = false,
    primary = Color(0xFFBFE673),
    onPrimary = Color(0xFF18240A),
    bg = Color(0xFF121210),
    surface = Color(0xFF1E1E1B),
    island = Color(0xFF2A2A25),
    text = Color(0xFFF4F3EC),
    text2 = Color(0xFF9B9B8F),
    tint = Color(0xFFBFE673).copy(alpha = .13f),
    wave = Color(0xFFBFE673).copy(alpha = .30f),
    ga = Color(0xFF34342E),
    gb = Color(0xFF1B1B18),
    rimHi = Color.White.copy(alpha = .30f),
    rimLo = Color.White.copy(alpha = .04f),
    gsh = Color.Black.copy(alpha = .18f),
    gglow = Color.White.copy(alpha = .06f),
)

val LocalColors = staticCompositionLocalOf { LightColors }

fun cl(x: Float) = x.coerceIn(0f, 1f)
fun ez(x: Float) = x * x * (3 - 2 * x)

/** Roboto 400/500 (системный шрифт Android), line-height как в CSS. */
fun ts(weight: Int, size: Float, line: Float, color: Color, ls: Float = 0f) = TextStyle(
    color = color,
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = ls.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = BasicText(text, modifier = modifier, style = style, overflow = overflow, maxLines = maxLines)

@Composable
fun Ico(@DrawableRes id: Int, tint: Color, modifier: Modifier = Modifier) =
    Image(painterResource(id), null, modifier, colorFilter = ColorFilter.tint(tint))

/** linear-gradient(angle, ...) из CSS: тот же угол и та же длина линии градиента. */
fun cssBrush(size: Size, angleDeg: Float, vararg stops: Pair<Float, Color>): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = (-cos(a)).toFloat()
    val len = abs(size.width * dx) + abs(size.height * dy)
    val c = Offset(size.width / 2f, size.height / 2f)
    val d = Offset(dx, dy) * (len / 2f)
    return Brush.linearGradient(*stops, start = c - d, end = c + d)
}

fun Modifier.cssGradient(angleDeg: Float, vararg stops: Pair<Float, Color>): Modifier =
    drawBehind { drawRect(cssBrush(size, angleDeg, *stops)) }

/** Аватар: градиент 145° + буква, поверх фото (если не загрузилось — остаётся буква). */
@Composable
fun Ava(letter: String, url: String, size: Dp, fontSize: Float) {
    val c = LocalColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .cssGradient(145f, 0f to c.primary, 1f to Color(0xFF8DBE4A)),
        contentAlignment = Alignment.Center,
    ) {
        T(letter, ts(500, fontSize, fontSize, c.onPrimary))
        AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}
