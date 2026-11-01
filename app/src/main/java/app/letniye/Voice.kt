package app.letniye

import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** Цвета голосового из темы приложения (в чате — Accent/Ink/InputPill). */
class VoiceColors(
    val fill: Color,      // заливка островков и пилюли
    val primary: Color,   // кружок записи, волны
    val onPrimary: Color, // иконки на кружке
    val ink: Color,
    val soft: Color,
    val mute: Color,
    val rec: Color,       // мигающая точка записи
)

fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

// ---- порт BlobDrawable ----
class Blob(val n: Int) {
    var minR = 0f; var maxR = 0f
    private val radius = FloatArray(n); private val angle = FloatArray(n)
    private val radiusNext = FloatArray(n); private val angleNext = FloatArray(n)
    private val progress = FloatArray(n); private val speed = FloatArray(n)
    private val rnd = Random()
    val path = Path()
    private val l = (4.0 / 3.0 * tan(PI / (2 * n))).toFloat()
    var amp = 0f
    private var ampTo = 0f
    private var ampDiff = 0f

    private fun gen(r: FloatArray, a: FloatArray, i: Int) {
        val angleDif = 360f / n * 0.05f
        r[i] = minR + (rnd.nextInt(100) / 100f) * (maxR - minR)
        a[i] = 360f / n * i + (rnd.nextInt(100) / 100f) * angleDif
        speed[i] = (0.017 + 0.003 * (rnd.nextInt(100) / 100f)).toFloat()
    }

    fun generate() { for (i in 0 until n) { gen(radius, angle, i); gen(radiusNext, angleNext, i); progress[i] = 0f } }

    fun setValue(v: Float, big: Boolean) {
        // BlobDrawable.setValue: подъём быстрее спада, у big и tiny свои скорости
        ampTo = v
        ampDiff = if (big) {
            if (v > amp) (v - amp) / (100f + 300f * 0.35f)
            else (v - amp) / (100f + 500f * 0.35f)
        } else {
            if (v > amp) (v - amp) / (100f + 400f * 0.55f)
            else (v - amp) / (100f + 500f * 0.55f)
        }
    }

    fun tick(dt: Long, speedScale: Float) {
        amp += ampDiff * dt
        if ((ampDiff > 0 && amp > ampTo) || (ampDiff < 0 && amp < ampTo)) amp = ampTo
        for (i in 0 until n) {
            progress[i] += speed[i] * 0.8f + amp * speed[i] * 8.2f * speedScale
            if (progress[i] >= 1f) {
                progress[i] = 0f
                radius[i] = radiusNext[i]; angle[i] = angleNext[i]
                gen(radiusNext, angleNext, i)
            }
        }
    }

    fun draw(cx: Float, cy: Float, scope: DrawScope, color: Color) {
        path.reset()
        fun rot(px: Float, py: Float, deg: Float): Offset {
            val rad = deg * PI.toFloat() / 180f
            val c = cos(rad); val s = sin(rad)
            val dx = px - cx; val dy = py - cy
            return Offset(cx + dx * c - dy * s, cy + dx * s + dy * c)
        }
        for (i in 0 until n) {
            val p = progress[i]
            val ni = if (i + 1 < n) i + 1 else 0
            val pn = progress[ni]
            val r1 = radius[i] * (1f - p) + radiusNext[i] * p
            val r2 = radius[ni] * (1f - pn) + radiusNext[ni] * pn
            val a1 = angle[i] * (1f - p) + angleNext[i] * p
            val a2 = angle[ni] * (1f - pn) + angleNext[ni] * pn
            val len = l * (min(r1, r2) + (kotlin.math.max(r1, r2) - min(r1, r2)) / 2f)
            val s0 = rot(cx, cy - r1, a1)
            val s1 = rot(cx + len, cy - r1, a1)
            val e0 = rot(cx, cy - r2, a2)
            val e1 = rot(cx - len, cy - r2, a2)
            if (i == 0) path.moveTo(s0.x, s0.y)
            path.cubicTo(s1.x, s1.y, e1.x, e1.y, e0.x, e0.y)
        }
        scope.drawPath(path, color)
    }
}

// ---- контроллер записи ----
class VoiceRec {
    var recording by mutableStateOf(false)
    /** запись всё ещё нужна: палец не отпущен и не отменили */
    @Volatile var wantRec = false
    var locked by mutableStateOf(false)
    var timeText by mutableStateOf("0:00,0")
    val slide = mutableFloatStateOf(1f)
    /** кружок летит домой — садится ровно в кнопку микрофона */
    var returning by androidx.compose.runtime.mutableStateOf(false)
    /** slide в момент отпускания и радиус кружка тогда же (px) */
    var returnFrom = 1f
    var returnR0 = 0f

    // прогресс до лока (0..1 за 57dp вверх)
    val lockT = mutableFloatStateOf(0f)
    var startAt = 0L

    // амплитуда круга: как в RecordCircle (diff за мс /375)
    var amp = 0f
    private var ampTo = 0f
    private var ampDiff = 0f
    var lastFrame = 0L
    var wavesEnter = 0f
    // idleProgress из RecordCircle: пинг-понг 0..1 по 0.01/кадр — лёгкое
    // дыхание замка и подсказки «Влево — отмена»
    var idle = 0f
    private var incIdle = false
    fun tickIdle() {
        if (incIdle) { idle += 0.01f; if (idle > 1f) { idle = 1f; incIdle = false } }
        else { idle -= 0.01f; if (idle < 0f) { idle = 0f; incIdle = true } }
    }

    val big = Blob(12)
    val tiny = Blob(11)
    val samples = ArrayList<Float>()

    // превью после паузы (RECORD_STATE_PREPARING в TG)
    var preview by mutableStateOf(false)
    var prevRes: Triple<String, Int, List<Float>>? = null
    // обрезка в превью (AudioTimelineView): доли 0..1 от записи
    val trimL = mutableFloatStateOf(0f)
    val trimR = mutableFloatStateOf(1f)

    private var rec: MediaRecorder? = null
    var file: File? = null
    // запись сегментами: пауза завершает сегмент, resume открывает новый,
    // на выходе склейка (в TG toggleRecordingPause пишет в тот же поток)
    private val segs = ArrayList<File>()
    var accumMs = 0L

    private fun startSegment(ctx: android.content.Context): Boolean {
        return try {
            val dir = File(ctx.filesDir, "voice").apply { mkdirs() }
            val f = File(dir, "v${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // максимальное качество голоса: моно 48кГц/64кбит AAC.
            // Пролезает по BLE благодаря WRITE_TYPE_NO_RESPONSE + high priority
            r.setAudioChannels(1)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            rec = r; file = f
            segs.add(f)
            startAt = System.currentTimeMillis()
            recording = true
            true
        } catch (e: Exception) { rec = null; false }
    }

    /** Мгновенная часть: сбросить состояние, чтобы анимация пошла тем же кадром.
     *  MediaRecorder.prepare/start стоят 100-250мс на UI-потоке — из-за них
     *  кружок «появлялся уже большим» вместо плавного роста. */
    fun prepareUi() {
        segs.forEach { it.delete() }; segs.clear()
        accumMs = 0L
        samples.clear()
        trimL.floatValue = 0f; trimR.floatValue = 1f
        amp = 0f; ampTo = 0f; ampDiff = 0f
        wavesEnter = 0f; lastFrame = 0L
        big.generate(); tiny.generate()
        timeText = "0:00,0"
        slide.floatValue = 1f; lockT.floatValue = 0f
        returning = false; returnFrom = 1f; returnR0 = 0f
        locked = false
        preview = false; prevRes = null
        startAt = System.currentTimeMillis()
        wantRec = true
    }

    /** Тяжёлая часть: поднять MediaRecorder. Вызывать с фонового потока.
     *  Если за это время палец уже отпустили (wantRec = false), сразу гасим. */
    fun startRecorder(ctx: android.content.Context): Boolean {
        if (!wantRec) return false
        val ok = startSegment(ctx)
        if (ok && !wantRec) { stopRec(); segs.forEach { it.delete() }; segs.clear(); recording = false }
        return ok
    }

    fun begin(ctx: android.content.Context): Boolean {
        segs.forEach { it.delete() }; segs.clear()
        accumMs = 0L
        samples.clear()
        trimL.floatValue = 0f; trimR.floatValue = 1f
        amp = 0f; ampTo = 0f; ampDiff = 0f
        wavesEnter = 0f; lastFrame = 0L
        big.generate(); tiny.generate()
        timeText = "0:00,0"
        slide.floatValue = 1f; lockT.floatValue = 0f
        returning = false; returnFrom = 1f; returnR0 = 0f
        locked = false
        preview = false; prevRes = null
        return startSegment(ctx)
    }

    /** возобновить запись после паузы (transformToResume в TG) */
    fun resume(ctx: android.content.Context) {
        if (!preview) return
        preview = false
        // обрезка при возобновлении сбрасывается — окно снова вся запись
        trimL.floatValue = 0f; trimR.floatValue = 1f
        prevRes = null
        amp = 0f; ampTo = 0f; ampDiff = 0f
        wavesEnter = 0f; lastFrame = 0L
        big.generate(); tiny.generate()
        startSegment(ctx)
    }

    /** склейка сегментов + вырезка окна [startUs..endUs] без перекодирования */
    private fun remux(inputs: List<File>, out: File, startUs: Long, endUs: Long): Boolean {
        return try {
            val muxer = android.media.MediaMuxer(
                out.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            var ptsOffset = 0L
            var written = 0L
            val buf = java.nio.ByteBuffer.allocate(262144)
            val info = android.media.MediaCodec.BufferInfo()
            var started = false
            for (f in inputs) {
                val ex = android.media.MediaExtractor()
                ex.setDataSource(f.absolutePath)
                var idx = -1
                for (i in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(i)
                    if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        idx = i
                        if (track < 0) track = muxer.addTrack(fmt)
                        break
                    }
                }
                if (idx < 0) { ex.release(); continue }
                ex.selectTrack(idx)
                if (!started) { muxer.start(); started = true }
                var segLast = 0L
                while (true) {
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) break
                    val pts = ptsOffset + ex.sampleTime
                    segLast = pts
                    if (pts in startUs..endUs) {
                        info.set(0, n, written.coerceAtLeast(pts - startUs), 0)
                        muxer.writeSampleData(track, buf, info)
                        written = pts - startUs
                    }
                    ex.advance()
                }
                ptsOffset = segLast + 21333L // ~кадр AAC
                ex.release()
            }
            if (started) { muxer.stop() }
            muxer.release()
            true
        } catch (e: Exception) { out.delete(); false }
    }

    fun poll() {
        val r = rec ?: return
        val v = try { r.maxAmplitude } catch (e: Exception) { 0 }
        // TG кормит круг RMS (sqrt(sum/len/2), потолок 1800); maxAmplitude — ПИК
        // до 32767 (RMS·крест-фактор ~3.5·√2 ≈ 5) — без пересчёта волны вечно
        // на максимуме. Приводим пик к RMS-шкале TG.
        val a = min(1800f, v / 5f) / 1800f
        ampTo = a
        ampDiff = (a - amp) / 375f   // /(100+500·animationSpeedCircle), WaveDrawable
        big.setValue(a, true)
        tiny.setValue(a, false)
        samples.add(v.toFloat())
        val t = accumMs + (System.currentTimeMillis() - startAt)
        timeText = "%d:%02d,%d".format(t / 1000 / 60, t / 1000 % 60, t % 1000 / 100)
    }

    fun tickAmp(dt: Long) {
        amp += ampDiff * dt
        if ((ampDiff > 0 && amp > ampTo) || (ampDiff < 0 && amp < ampTo)) amp = ampTo
    }

    private fun waveBars(bars: Int): List<Float> {
        val wave = ArrayList<Float>(bars)
        if (samples.isEmpty()) samples.add(0f)
        for (i in 0 until bars) {
            val from = i * samples.size / bars
            val to = ((i + 1) * samples.size / bars).coerceAtLeast(from + 1)
            var m = 0f
            for (j in from until min(to, samples.size)) m = kotlin.math.max(m, samples[j])
            wave.add(m)
        }
        // как getWaveform2 в TG: высоты нормируются по максимуму записи;
        // sqrt поднимает тихие участки — без него волна выглядит пунктиром
        val mx = wave.max()
        return if (mx > 0f) wave.map { kotlin.math.sqrt(it / mx) } else wave
    }

    fun pausePreview() {
        val segMs = (System.currentTimeMillis() - startAt).toInt()
        stopRec()
        recording = false
        accumMs += segMs
        if (accumMs < 700) { cancel(); return }
        // несколько сегментов склеиваем сразу — превью-плееру нужен один файл
        if (segs.size > 1) {
            val merged = File(segs[0].parentFile, "m${System.currentTimeMillis()}.m4a")
            if (remux(segs, merged, 0L, Long.MAX_VALUE)) {
                segs.forEach { it.delete() }
                segs.clear(); segs.add(merged)
            } else { segs.subList(0, segs.size - 1).forEach { it.delete() }
                     val last = segs.last(); segs.clear(); segs.add(last) }
        }
        prevRes = Triple(segs[0].absolutePath, (accumMs / 1000).toInt().coerceAtLeast(1), waveBars(34))
        val sec = accumMs / 1000
        timeText = "%d:%02d,%d".format(sec / 60, sec % 60, accumMs % 1000 / 100)
        preview = true
    }

    fun finish(): Triple<String, Int, List<Float>>? {
        wantRec = false
        if (preview) {
            val res = prevRes
            val tl = trimL.floatValue; val tr = trimR.floatValue
            preview = false; recording = false; locked = false; prevRes = null
            if (res == null) return null
            // применяем обрезку (trimCurrentRecording в TG): вырезаем окно ремуксом
            if (tl > 0.001f || tr < 0.999f) {
                val src = File(res.first)
                val outF = File(src.parentFile, "t${System.currentTimeMillis()}.m4a")
                val totalUs = accumMs * 1000L
                if (remux(listOf(src), outF, (tl * totalUs).toLong(), (tr * totalUs).toLong())) {
                    src.delete()
                    val newMs = (accumMs * (tr - tl)).toLong()
                    val n = samples.size
                    val cut = if (n > 0) samples.subList(
                        (tl * n).toInt().coerceIn(0, n - 1),
                        (tr * n).toInt().coerceIn(1, n)) else samples
                    val keep = ArrayList(cut)
                    samples.clear(); samples.addAll(keep)
                    return Triple(outF.absolutePath, (newMs / 1000).toInt().coerceAtLeast(1), waveBars(34))
                }
            }
            return res
        }
        val segMs = (System.currentTimeMillis() - startAt).toInt()
        stopRec()
        recording = false; locked = false
        accumMs += segMs
        if (accumMs < 700) { cancel(); return null }
        if (segs.size > 1) {
            val merged = File(segs[0].parentFile, "m${System.currentTimeMillis()}.m4a")
            if (remux(segs, merged, 0L, Long.MAX_VALUE)) {
                segs.forEach { it.delete() }
                segs.clear(); segs.add(merged)
            }
        }
        if (segs.isEmpty() || !segs[0].exists()) return null
        return Triple(segs[0].absolutePath, (accumMs / 1000).toInt().coerceAtLeast(1), waveBars(34))
    }

    fun cancel() {
        wantRec = false
        stopRec()
        segs.forEach { it.delete() }; segs.clear()
        file?.delete()
        accumMs = 0L
        recording = false; locked = false; preview = false; prevRes = null
    }

    private fun stopRec() {
        try { rec?.stop() } catch (e: Exception) {}
        try { rec?.release() } catch (e: Exception) {}
        rec = null
    }
}

@Composable
fun VoiceRecordOverlay(
    ctrl: VoiceRec,
    vc: VoiceColors,
    /** лямбда, а не значение: анимация полёта читается в фазе отрисовки и не
     *  перекомпонует весь экран чата на каждом кадре */
    visibleTF: () -> Float,
    btnCenter: () -> Offset?,
    rootOffset: () -> Offset,
    onSend: () -> Unit,
    onResume: () -> Unit = {},
) {
    // пока кружок летит домой, оверлей жив в любом случае: recT гаснет своей
    // анимацией и успевает дойти до нуля раньше полёта — тогда круг пропадал,
    // а кнопка ещё скрыта, и в поле ввода моргала дырка
    val overlayOn by remember { derivedStateOf { visibleTF() > 0.01f || ctrl.returning } }
    // Векторы грузим ЗАРАНЕЕ, при открытии чата, а не в момент нажатия на
    // микрофон: инфляция пяти VectorDrawable занимала кадр ровно тогда, когда
    // начинался рост кружка, — отсюда «удержание начинается резко».
    val mic = painterResource(R.drawable.ic_mic)
    val plane = painterResource(R.drawable.ic_send)
    val lockOpen = painterResource(R.drawable.ic_lock_keyhole_minimalistic_unlocked_linear)
    val lockClosed = painterResource(R.drawable.ic_lock_keyhole_minimalistic_linear)
    val pausePainter = painterResource(R.drawable.ic_pause_bold)
    if (!overlayOn) { ctrl.lastFrame = 0L; return }
    val previewT by androidx.compose.animation.core.animateFloatAsState(
        if (ctrl.preview) 1f else 0f, tween(220, easing = LinearEasing), label = "prevT"
    )
    // кадровый пульс: инвалидирует только draw
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(ctrl.recording) {
        while (ctrl.recording) withFrameNanos { frame.longValue = it }
    }
    val sendT = remember { Animatable(0f) }
    LaunchedEffect(ctrl.locked) {
        if (ctrl.locked) sendT.animateTo(1f, tween(150, easing = LinearEasing))
        else sendT.snapTo(0f)
    }
    // лок-пилюля ВЫЛЕТАЕТ из самой кнопки записи: стартует у её края и
    // выезжает на своё место с замедлением, а не появляется на готовом
    val lockIn = remember { Animatable(0f) }
    LaunchedEffect(ctrl.recording) {
        if (ctrl.recording) {
            lockIn.snapTo(0f)
            lockIn.animateTo(1f, tween(340, delayMillis = 30,
                easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 0.85f, 0.28f, 1f)))
        } else lockIn.snapTo(0f)
    }
    // лок-пилюля после срабатывания растворяется
    val lockGone = remember { Animatable(0f) }
    LaunchedEffect(ctrl.locked) {
        if (ctrl.locked) {
            lockGone.snapTo(0f)
            lockGone.animateTo(1f, tween(280,
                easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        else lockGone.snapTo(0f)
    }
    // ВАЖНО: у Canvas нет pointerInput — иначе он, верхний на весь экран,
    // забирает хит-тест себе и «Отмена»/корзинка/плей/отправка мертвы
    Canvas(Modifier.fillMaxSize()) {
        frame.longValue // подписка на кадры
        val c = btnCenter() ?: return@Canvas
        val ro = rootOffset()
        val now = System.currentTimeMillis()
        val dt = if (ctrl.lastFrame == 0L) 16L else (now - ctrl.lastFrame).coerceAtMost(50L)
        ctrl.lastFrame = now
        if (ctrl.recording) {
            ctrl.tickAmp(dt)
            ctrl.big.tick(dt, 0.80f)
            ctrl.tiny.tick(dt, 0.84f)
            ctrl.tickIdle()
            if (ctrl.wavesEnter < 1f) ctrl.wavesEnter = min(1f, ctrl.wavesEnter + 0.04f)
        }
        val slide = ctrl.slide.floatValue
        val visibleT = visibleTF()
        // Раньше здесь стояла поп-кривая из RecordCircle (0 → 1 за половину
        // времени, потом просадка на 0.9 и обратно): кружок выскакивал из
        // ничего сразу большим. Теперь он ровно вырастает из самой кнопки —
        // тот же радиус, тот же центр, тот же цвет, поэтому подмены не видно.
        val sc = visibleT.coerceIn(0f, 1f)
        val dist = min(size.width * 0.35f, 140.dp.toPx())
        val slideDelta = -dist * (1f - slide)
        val cx = c.x - ro.x + slideDelta
        val cy = c.y - ro.y
        val slideScale = 0.7f + slide * 0.3f
        val hide = 1f - previewT
        // возврат домой: тот же самый кружок едет к кнопке и по дороге
        // становится ею — тот же радиус (20dp) и та же иконка. Ничего не
        // гаснет и не подменяется, поэтому подмена на живую кнопку не видна.
        val retP = if (ctrl.returning && ctrl.returnFrom < 1f)
            ((slide - ctrl.returnFrom) / (1f - ctrl.returnFrom)).coerceIn(0f, 1f) else 0f
        // прогресс уже сглажен самой анимацией — второй раз не ускоряем
        val retE = retP
        val circleA = 1f
        val csc = 1f
        val btnR = 24.dp.toPx()
        val fullR = 41.dp.toPx() + 30.dp.toPx() * ctrl.amp
        var radius = (btnR + (fullR - btnR) * sc) * slideScale * hide
        if (ctrl.returning) {
            if (ctrl.returnR0 <= 0f) ctrl.returnR0 = radius
            radius = ctrl.returnR0 + (24.dp.toPx() - ctrl.returnR0) * retE
        }
        // волны (EASE_OUT входа, масштабы SCALE_*_MIN + 1.4·amp)
        val slide1 = if (slide > 0.7f) 1f else slide / 0.7f
        val enter = 1f - (1f - ctrl.wavesEnter) * (1f - ctrl.wavesEnter)
        if (slide1 > 0f && ctrl.recording && !ctrl.returning && previewT < 0.05f) {
            // RecordCircle: big 50..50+12·FORM_BIG_MAX, tiny 47..47+15·FORM_SMALL_MAX
            ctrl.big.minR = 50.dp.toPx(); ctrl.big.maxR = 50.dp.toPx() + 12.dp.toPx() * 0.42f
            ctrl.tiny.minR = 47.dp.toPx(); ctrl.tiny.maxR = 47.dp.toPx() + 15.dp.toPx() * 0.42f
            var s = sc * slide1 * enter * (0.90f + 0.75f * ctrl.big.amp)
            scale(s, s, Offset(cx, cy)) { ctrl.big.draw(cx, cy, this, vc.primary.copy(alpha = 0.20f)) }
            s = sc * slide1 * enter * (0.94f + 0.75f * ctrl.tiny.amp)
            scale(s, s, Offset(cx, cy)) { ctrl.tiny.draw(cx, cy, this, vc.primary.copy(alpha = 0.10f)) }
        }
        // кружок вырастает из островка: заливка плавно переходит в цвет темы,
        // а по дороге домой — обратно в заливку островка
        val colorT = if (ctrl.returning) 1f - retE else sc
        val circleCol = lerp(vc.fill, vc.primary, colorT)
        if (radius > 0.5f) drawCircle(circleCol.copy(alpha = circleA), radius, Offset(cx, cy))
        // иконка: микрофон ↔ стрелка. Как в TG (drawIconInternal): не простое
        // затухание — уходящая иконка ужимается в точку, приходящая
        // разворачивается из точки, обе с альфой по прогрессу.
        // иконка тоже приходит к размеру кнопки (21dp), без подмены
        val icon = if (ctrl.returning) 28.dp.toPx() + (24.dp.toPx() - 28.dp.toPx()) * retE
            else 24.dp.toPx() + (28.dp.toPx() - 24.dp.toPx()) * sc
        if (hide > 0.05f) {
            val st = sendT.value
            val base = csc * hide * circleA
            if (st < 1f) {
                val si = icon * (1f - st)
                if (si > 0.5f) translate(cx - si / 2f, cy - si / 2f) {
                    with(mic) {
                        draw(Size(si, si), alpha = (1f - st) * base,
                            colorFilter = ColorFilter.tint(lerp(vc.ink, vc.onPrimary, colorT)))
                    }
                }
            }
            if (st > 0f) {
                val si = icon * st
                if (si > 0.5f) translate(cx - si / 2f, cy - si / 2f) {
                    with(plane) {
                        draw(Size(si, si), alpha = st * base,
                            colorFilter = ColorFilter.tint(vc.onPrimary))
                    }
                }
            }
        }
        val lg = lockGone.value
        if (ctrl.recording || ctrl.locked) {
            val lt = ctrl.lockT.floatValue
            val bx = c.x - ro.x
            // пилюля с замком → круг с паузой
            val pw = (34.dp.toPx() + 6.dp.toPx() * lg)
            val ph = 50.dp.toPx() - 10.dp.toPx() * lg
            // TG: пилюля ПОДНИМАЕТСЯ по мере тяги вверх (lockY уменьшается),
            // после срабатывания «падает» на своё место над кругом
            // RecordCircle.onDraw: lockY = ... + dpf2(30) * (1 - sc), то есть при
            // появлении замок выплывает снизу на 30dp, а не возникает на месте
            val pyTarget = cy - 128.dp.toPx() + 30.dp.toPx() * (1f - sc) -
                30.dp.toPx() * lt * (1f - lg) +
                (cy - 105.dp.toPx() - ph / 2f - (cy - 128.dp.toPx() - 30.dp.toPx())) * lg -
                8.dp.toPx() * ctrl.idle * (1f - lt) * (1f - lg)   // дыхание, как lockY в TG
            // вылет из кнопки: старт у её верхнего края, чуть сплющенным
            val li = if (ctrl.locked) 1f else lockIn.value
            val pyStart = cy - 26.dp.toPx() - ph
            val py = pyStart + (pyTarget - pyStart) * li
            // при отмене свайпом пилюля не возвращается вместе с кругом
            val a = sc * slide1 * (li / 0.30f).coerceAtMost(1f) *
                (if (ctrl.returning) 0f else 1f)
            if (a > 0.004f) {
                drawRoundRect(
                    vc.fill.copy(alpha = a), Offset(bx - pw / 2f, py), Size(pw, ph),
                    androidx.compose.ui.geometry.CornerRadius(pw / 2f)
                )
                // ---- замок → пауза: геометрический морф, точный порт TG
                // (RecordCircle.onDraw, transformToPauseProgress). Тело замка —
                // скруглённый прямоугольник 16×16, который сжимается до 12×12 и
                // разъезжается на две палочки паузы; дужка съезжает вниз и
                // прячется за телом; скважина (кружок фона) стягивается в точку.
                val ccx = bx
                val ccy = py + ph / 2f
                val d = { v: Float -> v * 1.dp.toPx() }
                val ico = vc.soft.copy(alpha = a * (1f - previewT))
                val bodyL = ccx - d(6f) - d(2f) * (1f - lg)
                val bodyR = ccx + d(6f) + d(2f) * (1f - lg)
                val bodyT = ccy - d(6f) - d(2f) * (1f - lg)
                val bodyB = ccy + d(6f) + d(2f) * (1f - lg)
                if (lg < 1f && previewT < 1f) {
                    // дужка: полукруг r=4dp + две ножки; левая ножка укорачивается
                    // по мере тяги вверх (moveProgress в TG) — замок «открывается»
                    val sx = ccx - d(4f)
                    val sy = bodyT - d(6f) - d(2f) * (1f - lt) + d(12f) * lg
                    val st = androidx.compose.ui.graphics.drawscope.Stroke(
                        d(1.7f), cap = StrokeCap.Round)
                    val alp = a * (1f - lg) * (1f - previewT)
                    clipRect(top = 0f, bottom = bodyB) {
                        drawArc(vc.soft.copy(alpha = alp), 180f, 180f, false,
                            Offset(sx, sy), Size(d(8f), d(8f)), style = st)
                        drawLine(vc.soft.copy(alpha = alp),
                            Offset(sx + d(8f), sy + d(4f)),
                            Offset(sx + d(8f), sy + d(6f) + d(4f) * (1f - lg)), d(1.7f),
                            StrokeCap.Round)
                        drawLine(vc.soft.copy(alpha = alp),
                            Offset(sx, sy + d(4f)),
                            Offset(sx, sy + d(4f) + d(4f) * ctrl.idle * (1f - lt)), d(1.7f),
                            StrokeCap.Round)
                    }
                }
                val iconScale = 1f - previewT
                if (lg > 0f && iconScale > 0.02f) scale(iconScale, iconScale, Offset(ccx, ccy)) {
                    // тело разъезжается на две палочки (gap 2·1.66dp, радиусы 3→1.5)
                    val gap = d(1.66f) * lg
                    val rOut = androidx.compose.ui.geometry.CornerRadius(
                        d(3f) + (d(1.5f) - d(3f)) * lg)
                    val rIn = androidx.compose.ui.geometry.CornerRadius(d(1.5f) * lg)
                    val pathL = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(
                            bodyL, bodyT, ccx - gap, bodyB,
                            topLeftCornerRadius = rOut, bottomLeftCornerRadius = rOut,
                            topRightCornerRadius = rIn, bottomRightCornerRadius = rIn))
                    }
                    val pathR = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(
                            ccx + gap, bodyT, bodyR, bodyB,
                            topRightCornerRadius = rOut, bottomRightCornerRadius = rOut,
                            topLeftCornerRadius = rIn, bottomLeftCornerRadius = rIn))
                    }
                    drawPath(pathL, ico); drawPath(pathR, ico)
                } else if (lg <= 0f) {
                    drawRoundRect(ico, Offset(bodyL, bodyT),
                        Size(bodyR - bodyL, bodyB - bodyT),
                        androidx.compose.ui.geometry.CornerRadius(d(3f)))
                }
                // скважина замка — кружок цвета пилюли, стягивается при морфе
                if (lg < 1f && previewT < 1f)
                    drawCircle(vc.fill.copy(alpha = a * (1f - previewT)),
                        d(2f) * (1f - lg), Offset(ccx, ccy))
                // пауза ↔ микрофон-резюм: замена иконки как в TG (масштаб + альфа)
                if (previewT > 0f) {
                    val li = 20.dp.toPx() * previewT
                    if (li > 0.5f) translate(ccx - li / 2f, ccy - li / 2f) {
                        with(mic) {
                            draw(Size(li, li), alpha = a * previewT,
                                colorFilter = ColorFilter.tint(vc.soft))
                        }
                    }
                }
            }
        }
    }
    // тап-таргеты поверх нарисованного. Координаты читаются В offset-лямбде:
    // она перевычисляется на каждом layout-проходе — хит-боксы едут вместе с
    // панелью (клавиатура/insets), а не замирают на позиции момента композиции.
    // Отправка компонуется ПЕРВОЙ: пауза выше по z и её нижний край не съедается.
    if (ctrl.locked) {
        Box(
            Modifier
                .offset {
                    val c = btnCenter() ?: return@offset androidx.compose.ui.unit.IntOffset(-9999, -9999)
                    val ro = rootOffset()
                    androidx.compose.ui.unit.IntOffset(
                        (c.x - ro.x - 48.dp.toPx()).toInt(),
                        (c.y - ro.y - 48.dp.toPx()).toInt())
                }
                .size(96.dp)
                .tap(onSend)
        )
        Box(
            Modifier
                .offset {
                    val c = btnCenter() ?: return@offset androidx.compose.ui.unit.IntOffset(-9999, -9999)
                    val ro = rootOffset()
                    androidx.compose.ui.unit.IntOffset(
                        (c.x - ro.x - 30.dp.toPx()).toInt(),
                        (c.y - ro.y - 90.dp.toPx() - 30.dp.toPx()).toInt())
                }
                .size(60.dp)
                .tap { if (ctrl.preview) onResume() else ctrl.pausePreview() }
        )
    }
}

@Composable
fun RecordPanel(ctrl: VoiceRec, vc: VoiceColors, t: () -> Float, modifier: Modifier, onCancel: () -> Unit) {
    val density = LocalDensity.current
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(ctrl.recording) {
        while (ctrl.recording) withFrameNanos { frame.longValue = it }
    }
    if (ctrl.preview) {
        val res = ctrl.prevRes
        var playing by remember { mutableStateOf(false) }
        var prepared by remember { mutableStateOf(false) }
        val prog = remember { mutableFloatStateOf(0f) }
        val mp = remember { MediaPlayer() }
        DisposableEffect(Unit) { onDispose { try { mp.release() } catch (e: Exception) {} } }
        LaunchedEffect(playing) {
            while (playing) {
                val d = mp.duration
                if (d > 0) {
                    prog.floatValue = mp.currentPosition / d.toFloat()
                    // стоп на правой ручке обрезки
                    if (mp.currentPosition >= (ctrl.trimR.floatValue * d).toInt()) {
                        try { mp.pause() } catch (e: Exception) {}
                        playing = false
                    }
                }
                kotlinx.coroutines.delay(30)
            }
        }
        Row(
            modifier
                .padding(end = 56.dp)
                .graphicsLayer { alpha = t() }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .clip(RoundedCornerShape(26.dp))
                .background(vc.fill)
                .padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).tap { onCancel() }, contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_trash_bin_trash_linear, vc.primary, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(vc.primary)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).tap {
                        if (res == null) return@tap
                        try {
                            if (!prepared) {
                                mp.setDataSource(res.first)
                                mp.prepare()
                                mp.setOnCompletionListener { playing = false }
                                prepared = true
                            }
                            if (playing) { mp.pause(); playing = false }
                            else {
                                // играем только окно обрезки (AudioTimelineView)
                                val d = mp.duration
                                val lMs = (ctrl.trimL.floatValue * d).toInt()
                                val rMs = (ctrl.trimR.floatValue * d).toInt()
                                if (mp.currentPosition < lMs || mp.currentPosition >= rMs - 50)
                                    mp.seekTo(lMs)
                                mp.start(); playing = true
                            }
                        } catch (e: Exception) {}
                    },
                    contentAlignment = Alignment.Center
                ) {
                    TgPlayPauseIcon(
                        playing, vc.onPrimary, Modifier.size(28.dp)
                    )
                }
                // волна-сикбар
                val prevPath = remember { androidx.compose.ui.graphics.Path() }
                // обрезка как в AudioTimelineView: тянешь ручки [ ] за края окна
                Canvas(
                    Modifier.weight(1f).height(30.dp).padding(horizontal = 4.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val d = awaitFirstDown()
                                val w = size.width.toFloat()
                                val lx = ctrl.trimL.floatValue * w
                                val rx = ctrl.trimR.floatValue * w
                                val grab = 14.dp.toPx()
                                // ближняя ручка в радиусе захвата
                                val left = when {
                                    kotlin.math.abs(d.position.x - lx) <= grab &&
                                        kotlin.math.abs(d.position.x - lx) <=
                                        kotlin.math.abs(d.position.x - rx) -> true
                                    kotlin.math.abs(d.position.x - rx) <= grab -> false
                                    else -> return@awaitEachGesture
                                }
                                d.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == d.id } ?: break
                                    if (!ch.pressed) break
                                    val f = (ch.position.x / w).coerceIn(0f, 1f)
                                    if (left) ctrl.trimL.floatValue =
                                        f.coerceAtMost(ctrl.trimR.floatValue - 0.08f)
                                    else ctrl.trimR.floatValue =
                                        f.coerceAtLeast(ctrl.trimL.floatValue + 0.08f)
                                    ch.consume()
                                }
                            }
                        }
                ) {
                    val wave = res?.third ?: emptyList()
                    if (wave.isEmpty()) return@Canvas
                    val step = size.width / wave.size
                    val bw = (step * 0.62f).coerceAtMost(2.5.dp.toPx())
                    prevPath.rewind()
                    wave.forEachIndexed { i, aa ->
                        val h = 2.dp.toPx() + 16.dp.toPx() * aa
                        prevPath.addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                androidx.compose.ui.geometry.Rect(
                                    Offset(i * step, (size.height - h) / 2f), Size(bw, h)),
                                androidx.compose.ui.geometry.CornerRadius(bw / 2f)))
                    }
                    val lx = ctrl.trimL.floatValue * size.width
                    val rx = ctrl.trimR.floatValue * size.width
                    // вне окна — притушено
                    drawPath(prevPath, vc.onPrimary.copy(alpha = 0.30f))
                    clipRect(left = lx, right = rx) {
                        drawPath(prevPath, vc.onPrimary.copy(alpha = 0.54f))
                        val w = prog.floatValue * (size.width + bw)
                        if (w > lx) clipRect(right = w) { drawPath(prevPath, vc.onPrimary) }
                    }
                    // ручки: вертикальные скруглённые скобки
                    val hw = 3.dp.toPx(); val hh = size.height
                    drawRoundRect(vc.onPrimary, Offset(lx - hw / 2f, 0f), Size(hw, hh),
                        androidx.compose.ui.geometry.CornerRadius(hw / 2f))
                    drawRoundRect(vc.onPrimary, Offset(rx - hw / 2f, 0f), Size(hw, hh),
                        androidx.compose.ui.geometry.CornerRadius(hw / 2f))
                }
                T(
                    "%d:%02d".format((res?.second ?: 0) / 60, (res?.second ?: 0) % 60),
                    ts(400, 12f, 16f, vc.onPrimary).copy(fontFeatureSettings = "tnum")
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        return
    }
    Row(
        modifier
            .padding(end = 56.dp)
            .graphicsLayer { alpha = t() }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .clip(RoundedCornerShape(26.dp))
            .background(vc.fill)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(10.dp).graphicsLayer {
            translationX = (1f - t()) * with(density) { 20.dp.toPx() }
            scaleX = t(); scaleY = t()
        }) {
            frame.longValue
            val ms = System.currentTimeMillis() % 1200L
            val a = if (ms < 600) 1f - ms / 600f else (ms - 600) / 600f
            drawCircle(vc.rec.copy(alpha = a), size.minDimension / 2f, center)
        }
        Spacer(Modifier.width(10.dp))
        T(
            ctrl.timeText,
            ts(500, 16f, 22f, vc.ink).copy(fontFeatureSettings = "tnum"),
            Modifier.graphicsLayer {
                translationX = (1f - t()) * with(density) { 20.dp.toPx() }
                alpha = t()
            }
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!ctrl.locked) {
                Row(
                    Modifier.graphicsLayer {
                        frame.longValue
                        val dist = with(density) { 140.dp.toPx() }
                        translationX = -dist * (1f - ctrl.slide.floatValue) * 0.5f +
                            (1f - t()) * with(density) { 20.dp.toPx() }
                        translationY = -ctrl.idle * with(density) { 3.dp.toPx() }
                        alpha = t() * ctrl.slide.floatValue
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Ico(R.drawable.ic_alt_arrow_left_linear, vc.mute, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    T("Влево — отмена", ts(400, 16f, 22f, vc.soft))
                }
            } else {
                T(
                    "Отмена", ts(500, 16f, 22f, vc.primary),
                    Modifier.tap(onCancel).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
