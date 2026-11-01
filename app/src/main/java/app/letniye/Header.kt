package app.letniye

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

/**
 * Растворение шапки: p = 0 (шапка на месте) … 1 (растворилась).
 * Прокрутка вниз двигает p вперёд на dy/150, вверх — назад; через 140 мс тишины p
 * довозится до 0 или 1 за 240 мс. Всё пишется только в graphicsLayer — без перекомпозиций.
 */
@Stable
class HeaderState(val grid: LazyStaggeredGridState, private val density: Float) {
    var p by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)   // scrollTop в dp
    var tick by mutableIntStateOf(0)

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y == 0f) return Offset.Zero
            val d = -consumed.y / density
            y = if (!grid.canScrollBackward) 0f else max(0f, y + d)
            p = if (y < 8f) 0f else cl(p + d / 150f)
            tick++
            return Offset.Zero
        }
    }
}

@Composable
fun rememberHeaderState(grid: LazyStaggeredGridState): HeaderState {
    val density = LocalDensity.current.density
    val h = remember(grid) { HeaderState(grid, density) }
    LaunchedEffect(h) {
        snapshotFlow { h.tick }.collectLatest {
            delay(140)
            if (!h.grid.canScrollBackward) h.y = 0f
            val to = if (h.y < 8f || h.p < .4f) 0f else 1f
            animate(h.p, to, animationSpec = tween(240, easing = CubicBezierEasing(.215f, .61f, .355f, 1f))) { v, _ ->
                h.p = v
            }
        }
    }
    return h
}

/** Аватар и поиск: уезжают к краям вверх и уменьшаются. dir = -1 слева, +1 справа. */
private fun Modifier.dissolveSide(h: HeaderState, dir: Float) = graphicsLayer {
    val a = ez(cl(h.p * 1.7f))
    alpha = 1f - a
    translationX = dir * a * 18.dp.toPx()
    translationY = -a * 10.dp.toPx()
    val s = 1f - .25f * a
    scaleX = s
    scaleY = s
}

/** Логотип: уезжает вверх чуть позже. */
private fun Modifier.dissolveTitle(h: HeaderState) = graphicsLayer {
    val t = ez(cl(h.p * 1.7f - .12f))
    alpha = 1f - t
    translationY = -t * 22.dp.toPx()
    val s = 1f - .08f * t
    scaleX = s
    scaleY = s
}

@Composable
fun Header(h: HeaderState, onThemeToggle: () -> Unit) {
    val c = LocalColors.current
    val active by remember { derivedStateOf { h.p <= .6f } }
    val toggle by rememberUpdatedState(onThemeToggle)
    val activeNow by rememberUpdatedState(active)
    val none = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // аватар
            Box(
                Modifier
                    .size(48.dp)
                    .dissolveSide(h, -1f)
                    .clickable(enabled = active, interactionSource = none, indication = null) { /* профиль — позже */ },
                contentAlignment = Alignment.Center,
            ) {
                Ava("И", "https://randomuser.me/api/portraits/men/32.jpg", 34.dp, 15f)
            }
            // логотип + название; долгое нажатие переключает тему (замена кнопки из прототипа)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .dissolveTitle(h)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { if (activeNow) toggle() }) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Ico(R.drawable.ic_logo, c.primary, Modifier.size(26.dp))
                    T(
                        "letniye",
                        ts(500, 20f, 28f, c.text, .1f),
                    )
                }
            }
            // поиск
            Box(
                Modifier
                    .size(48.dp)
                    .dissolveSide(h, 1f)
                    .clickable(enabled = active, interactionSource = none, indication = null) { /* поиск — позже */ },
                contentAlignment = Alignment.Center,
            ) {
                Ico(R.drawable.ic_search, c.primary, Modifier.size(26.dp))
            }
        }
    }
}

/**
 * Вуаль, как в чате (wallVeil): 13 ступеней цвета фона, у края «полка» полной
 * непрозрачности (hold — доля высоты), дальше smoothstep до нуля.
 */
private fun veilBrush(bg: Color, fromTop: Boolean, hold: Float, strength: Float): Brush {
    val stops = Array(13) { i ->
        val t = i / 12f
        val u = if (fromTop) ((t - hold) / (1f - hold)).coerceIn(0f, 1f)
                else ((1f - t - hold) / (1f - hold)).coerceIn(0f, 1f)
        val sm = u * u * (3f - 2f * u)
        t to bg.copy(alpha = strength * (1f - sm))
    }
    return Brush.verticalGradient(*stops)
}

/**
 * Верхняя вуаль: прогрессивное размытие (Haze, как в iOS) + лёгкий фейд цветом фона.
 * Размытие сильнее у верхнего края и плавно сходит на нет вниз. В покое слой прозрачен,
 * растёт за первые 30dp прокрутки, когда шапка растворилась — снова 0.
 */
@Composable
fun TopFade(h: HeaderState, haze: HazeState, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val brush = remember(c.bg) { veilBrush(c.bg, fromTop = true, hold = 0f, strength = .5f) }
    Box(
        modifier
            .fillMaxWidth()
            .height(inset + 98.dp)
            .graphicsLayer { alpha = (1f - ez(cl(h.p * 1.15f))) * ez(cl(h.y / 30f)) }
            .hazeEffect(haze) {
                blurRadius = 14.dp
                noiseFactor = 0f
                progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
            }
            .background(brush),
    )
}

/**
 * Нижняя вуаль над островком: то же прогрессивное размытие, сильнее у нижнего края.
 * Поднимается вместе с клавиатурой, как островок.
 */
@Composable
fun BottomFade(haze: HazeState, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val brush = remember(c.bg) { veilBrush(c.bg, fromTop = false, hold = 0f, strength = .6f) }
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .height(nav + 92.dp)
            .hazeEffect(haze) {
                blurRadius = 14.dp
                noiseFactor = 0f
                progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
            }
            .background(brush),
    )
}
