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
 * Шапка не тянется за пальцем: как только направление прокрутки определилось
 * (вниз на 16dp / вверх на 10dp), она сама доезжает до конца за 240 мс.
 * Всё пишется только в graphicsLayer — без перекомпозиций.
 */
@Stable
class HeaderState(val grid: LazyStaggeredGridState, private val density: Float) {
    var p by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)   // scrollTop в dp
    var target by mutableFloatStateOf(0f)
    private var acc = 0f

    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y == 0f) return Offset.Zero
            val d = -consumed.y / density
            y = if (!grid.canScrollBackward) 0f else max(0f, y + d)
            if (y < 8f) {
                acc = 0f
                target = 0f
            } else {
                if ((d > 0f) != (acc > 0f)) acc = 0f
                acc += d
                if (acc > 16f) target = 1f else if (acc < -10f) target = 0f
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberHeaderState(grid: LazyStaggeredGridState): HeaderState {
    val density = LocalDensity.current.density
    val h = remember(grid) { HeaderState(grid, density) }
    LaunchedEffect(h) {
        snapshotFlow { h.target }.collectLatest { to ->
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
 * Классическая подложка под шапкой (вместо фейда): сплошной цвет фона высотой в статус-бар + шапку.
 * Анимация прежняя: в покое 0, растёт за первые 30dp прокрутки, когда шапка растворилась — снова 0.
 */
@Composable
fun TopFade(h: HeaderState, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(inset + 56.dp)
            .graphicsLayer { alpha = (1f - ez(cl(h.p * 1.15f))) * ez(cl(h.y / 30f)) }
            .background(c.bg),
    )
}
