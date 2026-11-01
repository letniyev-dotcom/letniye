package app.letniye

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Иконка play/pause как в Telegram — порт PathAnimator + Theme.playPauseAnimator
 * + хореографии из MediaActionDrawable (поворот 0→95→90 и морф пути по SVG-кейфреймам).
 * Кейфреймы и все константы скопированы из исходников TG дословно.
 */
private class TgPathAnimator(
    private val scale: Float,
    private val tx: Float,
    private val ty: Float
) {
    private class Frame(val time: Float, val types: List<Char>, val cmds: List<FloatArray>)

    private val frames = ArrayList<Frame>()
    private val path = Path()
    private var pathTime = -1f
    private var pathDensity = -1f

    fun addSvgKeyFrame(svg: String, ms: Float) {
        val args = svg.split(" ")
        val types = ArrayList<Char>()
        val cmds = ArrayList<FloatArray>()
        var a = 0
        while (a < args.size) {
            when (args[a][0]) {
                'M', 'L' -> {
                    types.add(args[a][0])
                    cmds.add(
                        floatArrayOf(
                            (args[a + 1].toFloat() + tx) * scale,
                            (args[a + 2].toFloat() + ty) * scale
                        )
                    )
                    a += 3
                }
                'C' -> {
                    types.add('C')
                    cmds.add(FloatArray(6) { i ->
                        (args[a + 1 + i].toFloat() + if (i % 2 == 0) tx else ty) * scale
                    })
                    a += 7
                }
                else -> a++
            }
        }
        frames.add(Frame(ms, types, cmds))
    }

    /** density = px на 1dp (аналог AndroidUtilities.dpf2) */
    fun draw(canvas: Canvas, paint: Paint, time: Float, density: Float) {
        if (pathTime != time || pathDensity != density) {
            pathTime = time; pathDensity = density
            var start: Frame? = null
            var end: Frame? = null
            for (f in frames) {
                if ((start == null || start.time < f.time) && f.time <= time) start = f
                if ((end == null || end.time > f.time) && f.time >= time) end = f
            }
            if (end == start) start = null
            if (start != null && end == null) { end = start; start = null }
            if (end == null || (start != null && start.types.size != end.types.size)) return
            val progress =
                if (start != null) (time - start.time) / (end.time - start.time) else 1f
            path.reset()
            for (a in end.types.indices) {
                val e = end.cmds[a]
                val s = start?.cmds?.get(a)
                fun v(i: Int) =
                    (if (s != null) s[i] + (e[i] - s[i]) * progress else e[i]) * density
                when (end.types[a]) {
                    'M' -> path.moveTo(v(0), v(1))
                    'L' -> path.lineTo(v(0), v(1))
                    'C' -> path.cubicTo(v(0), v(1), v(2), v(3), v(4), v(5))
                }
            }
            path.close()
        }
        canvas.drawPath(path, paint)
    }
}

// Theme.playPauseAnimator: те же параметры и кейфреймы, что в TG
private val playPauseAnimator = TgPathAnimator(0.293f, -26f, -28f).apply {
    addSvgKeyFrame("M 34.141 16.042 C 37.384 17.921 40.886 20.001 44.211 21.965 C 46.139 23.104 49.285 24.729 49.586 25.917 C 50.289 28.687 48.484 30 46.274 30 L 6 30.021 C 3.79 30.021 2.075 30.023 2 26.021 L 2.009 3.417 C 2.009 0.417 5.326 -0.58 7.068 0.417 C 10.545 2.406 25.024 10.761 34.141 16.042 Z", 166f)
    addSvgKeyFrame("M 37.843 17.769 C 41.143 19.508 44.131 21.164 47.429 23.117 C 48.542 23.775 49.623 24.561 49.761 25.993 C 50.074 28.708 48.557 30 46.347 30 L 6 30.012 C 3.79 30.012 2 28.222 2 26.012 L 2.009 4.609 C 2.009 1.626 5.276 0.664 7.074 1.541 C 10.608 3.309 28.488 12.842 37.843 17.769 Z", 200f)
    addSvgKeyFrame("M 40.644 18.756 C 43.986 20.389 49.867 23.108 49.884 25.534 C 49.897 27.154 49.88 24.441 49.894 26.059 C 49.911 28.733 48.6 30 46.39 30 L 6 30.013 C 3.79 30.013 2 28.223 2 26.013 L 2.008 5.52 C 2.008 2.55 5.237 1.614 7.079 2.401 C 10.656 4 31.106 14.097 40.644 18.756 Z", 217f)
    addSvgKeyFrame("M 43.782 19.218 C 47.117 20.675 50.075 21.538 50.041 24.796 C 50.022 26.606 50.038 24.309 50.039 26.104 C 50.038 28.736 48.663 30 46.453 30 L 6 29.986 C 3.79 29.986 2 28.196 2 25.986 L 2.008 6.491 C 2.008 3.535 5.196 2.627 7.085 3.316 C 10.708 4.731 33.992 14.944 43.782 19.218 Z", 234f)
    addSvgKeyFrame("M 47.421 16.941 C 50.544 18.191 50.783 19.91 50.769 22.706 C 50.761 24.484 50.76 23.953 50.79 26.073 C 50.814 27.835 49.334 30 47.124 30 L 5 30.01 C 2.79 30.01 1 28.22 1 26.01 L 1.001 10.823 C 1.001 8.218 3.532 6.895 5.572 7.26 C 7.493 8.01 47.421 16.941 47.421 16.941 Z", 267f)
    addSvgKeyFrame("M 47.641 17.125 C 50.641 18.207 51.09 19.935 51.078 22.653 C 51.07 24.191 51.062 21.23 51.088 23.063 C 51.109 24.886 49.587 27 47.377 27 L 5 27.009 C 2.79 27.009 1 25.219 1 23.009 L 0.983 11.459 C 0.983 8.908 3.414 7.522 5.476 7.838 C 7.138 8.486 47.641 17.125 47.641 17.125 Z", 300f)
    addSvgKeyFrame("M 48 7 C 50.21 7 52 8.79 52 11 C 52 19 52 19 52 19 C 52 21.21 50.21 23 48 23 L 4 23 C 1.79 23 0 21.21 0 19 L 0 11 C 0 8.79 1.79 7 4 7 C 48 7 48 7 48 7 Z", 383f)
}

// CubicBezierInterpolator.EASE_BOTH из TG
private val EaseBoth = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
// его правка к оригиналу TG: слегка скруглённые краешки у треугольника и палочек
private var effectDensity = -1f
private fun ensureCorner(density: Float) {
    if (effectDensity != density) {
        effectDensity = density
        iconPaint.pathEffect = android.graphics.CornerPathEffect(1.5f * density)
    }
}

/**
 * Рисует play/pause по центру DrawScope.
 * @param p 0 = play, 1 = pause (анимировать линейно 300мс, как в TG)
 * @param toPause true, если текущий переход — play→pause (ветки поворота различаются)
 */
fun DrawScope.drawTgPlayPause(p: Float, toPause: Boolean, color: Color) {
    var ms = 500f * p
    val rotation: Float
    if (toPause) {
        rotation = when {
            ms < 384f -> 95f * EaseBoth.transform(ms / 384f)
            ms < 484f -> 95f - 5f * EaseBoth.transform((ms - 384f) / 100f)
            else -> 90f
        }
        ms += 100f
    } else {
        rotation = when {
            ms < 100f -> -5f * EaseBoth.transform(ms / 100f)
            ms < 484f -> -5f + 95f * EaseBoth.transform((ms - 100f) / 384f)
            else -> 90f
        }
    }
    iconPaint.color = color.toArgb()
    ensureCorner(density)
    drawIntoCanvas { c ->
        val n = c.nativeCanvas
        n.save()
        n.translate(center.x + 1.dp.toPx() * (1f - p), center.y)
        n.rotate(rotation)
        playPauseAnimator.draw(n, iconPaint, ms, density)
        n.scale(1f, -1f)
        playPauseAnimator.draw(n, iconPaint, ms, density)
        n.restore()
    }
}

/** Плавность переключения play↔pause: 400мс с мягкими концами (EASE_BOTH).
 *  В TG это 300мс линейно, но там сам морф укладывается в ~130мс из них и
 *  выглядит рвано на 120Гц — его правка 27.08 02:38 («дёрганно, резко»). */
private val PlayPauseDur = 400
private val PlayPauseEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/**
 * Готовая иконка play/pause: сама держит анимацию и направление перехода.
 * Направление ставится ДО старта анимации (раньше это делал LaunchedEffect
 * уже после первого кадра — ветка поворота на кадр оставалась старой).
 */
@androidx.compose.runtime.Composable
fun TgPlayPauseIcon(
    playing: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    /** Масштаб видимого пути внутри слота. По умолчанию сохраняем TG-мерку. */
    iconScale: Float = 1f
) {
    val p = androidx.compose.runtime.remember {
        androidx.compose.animation.core.Animatable(if (playing) 1f else 0f)
    }
    val dir = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(playing)
    }
    androidx.compose.runtime.LaunchedEffect(playing) {
        dir.value = playing
        p.animateTo(
            if (playing) 1f else 0f,
            androidx.compose.animation.core.tween(PlayPauseDur, easing = PlayPauseEase)
        )
    }
    androidx.compose.foundation.Canvas(modifier) {
        val v = p.value
        // Путь рисуется с CornerPathEffect — его нельзя закешировать в GPU,
        // и на каждый кадр прокрутки каждая строка списка пересобирала
        // геометрию (лаг «В телефоне», его баг 27.08 04:21). В покое иконка
        // одна и та же — печём её один раз в битмапу и просто кладём.
        if (iconScale == 1f && !p.isRunning && (v == 0f || v == 1f)) {
            val img = staticIcon(
                size.width.toInt(), size.height.toInt(), v == 1f, color, density
            )
            if (img != null) {
                drawImage(img)
                return@Canvas
            }
        }
        if (iconScale == 1f) {
            drawTgPlayPause(v, dir.value, color)
        } else {
            drawIntoCanvas { c ->
                c.save()
                c.translate(center.x, center.y)
                c.scale(iconScale, iconScale)
                c.translate(-center.x, -center.y)
                drawTgPlayPause(v, dir.value, color)
                c.restore()
            }
        }
    }
}

/** Кэш готовых иконок покоя: ключ — размер, состояние, цвет и плотность. */
private val staticIcons = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

private fun staticIcon(
    w: Int, h: Int, pause: Boolean, color: androidx.compose.ui.graphics.Color, density: Float
): androidx.compose.ui.graphics.ImageBitmap? {
    if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null
    val key = "$w:$h:$pause:${color.toArgb()}:$density"
    staticIcons[key]?.let { return it }
    val img = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(density),
        androidx.compose.ui.unit.LayoutDirection.Ltr,
        canvas,
        androidx.compose.ui.geometry.Size(w.toFloat(), h.toFloat())
    ) { drawTgPlayPause(if (pause) 1f else 0f, pause, color) }
    if (staticIcons.size > 24) staticIcons.clear()
    staticIcons[key] = img
    return img
}
