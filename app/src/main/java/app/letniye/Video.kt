package app.letniye

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Видео как оно записано: путь к mp4, секунды, размер кадра уже с учётом поворота. */
class VideoClip(val path: String, val sec: Int, val w: Int, val h: Int)

/**
 * Готовый кружок: тот же mp4 + положение круглой рамки в кадре.
 * cx, cy — центр рамки, cd — её диаметр; всё в долях ширины/высоты кадра (cd — в долях ширины).
 */
class VideoResult(
    val path: String, val sec: Int, val w: Int, val h: Int,
    val cx: Float, val cy: Float, val cd: Float,
)

fun fmtSec(s: Int) = (s / 60).toString() + ":" + (s % 60).toString().padStart(2, '0')

private const val MAX_MS = 60_000L
/** Камера всегда чёрная; акцент — светлый салатовый из тёмной темы. */
private val CamAccent = Color(0xFFBFE673)

private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun probe(path: String): VideoClip {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(path)
        var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
        var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        return VideoClip(path, ((ms + 500L) / 1000L).toInt(), w, h)
    } catch (e: Exception) {
        return VideoClip(path, 0, 720, 1280)
    } finally {
        try { r.release() } catch (e: Exception) {}
    }
}

// ───────────── проигрыватель: видео в TextureView (его можно обрезать кругом и двигать) ─────────────
private class VideoHolder(val path: String) {
    var mp: MediaPlayer? = null
    var surface: Surface? = null
    var vol = 0f

    val listener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { start(Surface(st)) }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    fun start(s: Surface) {
        release()
        try {
            val m = MediaPlayer()
            m.setDataSource(path)
            m.setSurface(s)
            m.isLooping = true
            m.setVolume(vol, vol)
            m.setOnPreparedListener { it.start() }
            m.prepareAsync()
            mp = m
            surface = s
        } catch (e: Exception) {
            try { s.release() } catch (e2: Exception) {}
        }
    }

    fun volume(v: Float) {
        vol = v
        try { mp?.setVolume(v, v) } catch (e: Exception) {}
    }

    fun release() {
        try { mp?.release() } catch (e: Exception) {}
        mp = null
        try { surface?.release() } catch (e: Exception) {}
        surface = null
    }
}

/** Зацикленное видео. Размер задаёт вызывающий: кадр растягивается на весь view. */
@Composable
fun LoopVideo(path: String, modifier: Modifier = Modifier, sound: Boolean = false) {
    val holder = remember(path) { VideoHolder(path) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    LaunchedEffect(holder, sound) { holder.volume(if (sound) 1f else 0f) }
    AndroidView(
        factory = { c -> TextureView(c).also { it.surfaceTextureListener = holder.listener } },
        modifier = modifier,
    )
}

// ───────────── экран камеры ─────────────
@Composable
private fun CamBtn(icon: Int, tint: Color = CamAccent, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.size(48.dp).tap(onClick), contentAlignment = Alignment.Center) {
        Ico(icon, tint, Modifier.size(26.dp))
    }

/**
 * Своя камера на весь экран, фон чёрный. Сначала пишется обычное видео,
 * потом на нём выбирается круглая рамка: двигать одним пальцем, тянуть двумя.
 */
@Composable
fun VideoCamera(onClose: () -> Unit, onDone: (VideoResult) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    // на чёрном нужны светлые значки статус-бара
    DisposableEffect(Unit) {
        val win = ctx.activity()?.window
        val ctl = if (win != null) WindowCompat.getInsetsController(win, view) else null
        val prevS = ctl?.isAppearanceLightStatusBars
        val prevN = ctl?.isAppearanceLightNavigationBars
        ctl?.isAppearanceLightStatusBars = false
        ctl?.isAppearanceLightNavigationBars = false
        onDispose {
            if (prevS != null) ctl?.isAppearanceLightStatusBars = prevS
            if (prevN != null) ctl?.isAppearanceLightNavigationBars = prevN
        }
    }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    var camOk by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var micOk by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        camOk = granted(Manifest.permission.CAMERA)
        micOk = granted(Manifest.permission.RECORD_AUDIO)
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!camOk || !micOk) ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        else asked = true
    }

    var clip by remember { mutableStateOf<VideoClip?>(null) }
    val cur = clip
    BackHandler {
        if (cur != null) { File(cur.path).delete(); clip = null } else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // экран камеры закрывает ленту: касания под ним не проходят
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        when {
            cur != null -> Review(cur, onRetake = { File(cur.path).delete(); clip = null }, onSend = onDone)
            camOk -> Capture(micOk, onClip = { clip = it }, onClose = onClose)
            asked -> NoAccess(onClose)
            else -> {}
        }
    }
}

@Composable
private fun NoAccess(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        CamBtn(
            R.drawable.ic_x,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 4.dp, top = 4.dp),
        ) { onClose() }
        T(
            "Нужен доступ к камере. Включите его в настройках приложения.",
            ts(400, 15f, 22f, Color.White.copy(alpha = .8f)).copy(textAlign = TextAlign.Center),
            Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
        )
    }
}

@SuppressLint("MissingPermission")
private fun startRec(
    ctx: Context, cap: VideoCapture<Recorder>, f: File, audio: Boolean, on: (VideoRecordEvent) -> Unit,
): Recording {
    val o = FileOutputOptions.Builder(f).setDurationLimitMillis(MAX_MS).build()
    var p = cap.output.prepareRecording(ctx, o)
    if (audio) p = p.withAudioEnabled()
    return p.start(ContextCompat.getMainExecutor(ctx)) { on(it) }
}

@Composable
private fun Capture(micOk: Boolean, onClip: (VideoClip) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = remember { ctx.activity() as? LifecycleOwner }
    val scope = rememberCoroutineScope()
    val exec = remember { ContextCompat.getMainExecutor(ctx) }
    var front by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var ms by remember { mutableStateOf(0L) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cap by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val rec = remember { arrayOfNulls<Recording>(1) }
    val preview = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // привязка камеры; при смене фронтальная/основная — заново
    DisposableEffect(front) {
        val fut = ProcessCameraProvider.getInstance(ctx)
        var prov: ProcessCameraProvider? = null
        fut.addListener({
            try {
                val o = owner ?: return@addListener
                val p = fut.get()
                prov = p
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                p.unbindAll()
                camera = p.bindToLifecycle(
                    o,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    pv, vc,
                )
                cap = vc
            } catch (e: Exception) {
            }
        }, exec)
        onDispose {
            try { prov?.unbindAll() } catch (e: Exception) {}
            cap = null
            camera = null
        }
    }
    LaunchedEffect(torch, camera) {
        try { camera?.cameraControl?.enableTorch(torch) } catch (e: Exception) {}
    }
    DisposableEffect(Unit) {
        onDispose {
            try { rec[0]?.stop() } catch (e: Exception) {}
            rec[0] = null
        }
    }

    fun toggle() {
        val r = rec[0]
        if (r != null) { r.stop(); rec[0] = null; return } // результат придёт событием Finalize
        val vc = cap ?: return
        val dir = File(ctx.filesDir, "video").apply { mkdirs() }
        val f = File(dir, "c_" + System.currentTimeMillis() + ".mp4")
        ms = 0L
        recording = true
        rec[0] = startRec(ctx, vc, f, micOk) { ev ->
            when (ev) {
                is VideoRecordEvent.Status -> ms = ev.recordingStats.recordedDurationNanos / 1_000_000L
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    rec[0] = null
                    val ok = ev.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                        ev.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                    if (ok && f.length() > 0L) {
                        scope.launch {
                            val c = withContext(Dispatchers.IO) { probe(f.absolutePath) }
                            onClip(c)
                        }
                    } else f.delete()
                }
                else -> {}
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))),
        )
        // верхняя панель: закрыть и название / таймер
        Box(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp)) {
            CamBtn(R.drawable.ic_x, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) { onClose() }
            T(
                if (recording) fmtSec((ms / 1000L).toInt()) else "Видео",
                ts(500, 16f, 22f, if (recording) CamAccent else Color.White),
                Modifier.align(Alignment.Center),
            )
        }
        // полоса прогресса до 60 секунд
        Box(
            Modifier
                .statusBarsPadding()
                .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(4.dp)
                .graphicsLayer { alpha = if (recording) 1f else 0f }
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = .18f)),
        ) {
            Box(
                Modifier.fillMaxWidth((ms.toFloat() / MAX_MS).coerceIn(0f, 1f)).fillMaxHeight().background(CamAccent),
            )
        }
        // низ: переворот · запись · вспышка
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(
                if (recording) "Идёт запись" else "Нажмите, чтобы записать",
                ts(400, 14f, 20f, Color.White.copy(alpha = .7f)),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.size(48.dp)) {
                    if (!recording) CamBtn(R.drawable.ic_flip) { front = !front; torch = false }
                }
                Box(Modifier.size(80.dp).tap { toggle() }, contentAlignment = Alignment.Center) {
                    Crossfade(recording, label = "rec") { r ->
                        Ico(if (r) R.drawable.ic_stop else R.drawable.ic_rec, CamAccent, Modifier.size(80.dp))
                    }
                }
                Box(Modifier.size(48.dp)) {
                    if (!recording && camera?.cameraInfo?.hasFlashUnit() == true) {
                        CamBtn(R.drawable.ic_bolt, if (torch) CamAccent else Color.White.copy(alpha = .55f)) { torch = !torch }
                    }
                }
            }
        }
    }
}

// ───────────── рамка-кружок на готовом видео ─────────────
@Composable
private fun Review(clip: VideoClip, onRetake: () -> Unit, onSend: (VideoResult) -> Unit) {
    val d = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = with(d) { maxWidth.toPx() }
        val bh = with(d) { maxHeight.toPx() }
        val ar = clip.w.toFloat() / clip.h
        // видео целиком по центру
        val vw = min(bw, bh * ar)
        val vh = vw / ar
        val vx = (bw - vw) / 2f
        val vy = (bh - vh) / 2f
        val side = min(vw, vh)
        val lo = min(side, with(d) { 96.dp.toPx() })
        var fd by remember(bw, bh) { mutableFloatStateOf(side * .8f) }
        var fx by remember(bw, bh) { mutableFloatStateOf(bw / 2f) }
        var fy by remember(bw, bh) { mutableFloatStateOf(bh / 2f) }

        LoopVideo(
            clip.path,
            Modifier
                .offset { IntOffset(vx.roundToInt(), vy.roundToInt()) }
                .size(with(d) { vw.toDp() }, with(d) { vh.toDp() }),
        )
        // всё вне рамки затемнено; рамку двигаем и масштабируем жестами
        Canvas(
            Modifier.fillMaxSize().pointerInput(vx, vy, vw, vh) {
                detectTransformGestures { _, pan, zoom, _ ->
                    fd = (fd * zoom).coerceIn(lo, side)
                    fx = (fx + pan.x).coerceIn(vx + fd / 2f, vx + vw - fd / 2f)
                    fy = (fy + pan.y).coerceIn(vy + fd / 2f, vy + vh - fd / 2f)
                }
            },
        ) {
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(Offset(fx, fy), fd / 2f))
            }
            drawPath(p, Color.Black.copy(alpha = .68f))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))),
        )
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(56.dp)) {
            T("Кружок", ts(500, 16f, 22f, Color.White), Modifier.align(Alignment.Center))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T("Двигайте и масштабируйте рамку", ts(400, 14f, 20f, Color.White.copy(alpha = .7f)))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CamBtn(R.drawable.ic_redo) { onRetake() }
                Box(
                    Modifier.size(72.dp).tap {
                        onSend(VideoResult(clip.path, clip.sec, clip.w, clip.h, (fx - vx) / vw, (fy - vy) / vh, fd / vw))
                    },
                    contentAlignment = Alignment.Center,
                ) { Ico(R.drawable.ic_send, CamAccent, Modifier.size(40.dp)) }
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** Кружок в ленте: тот же кадр, сдвинутый и увеличенный так, чтобы рамка заполнила круг. */
@Composable
fun CircleVideo(p: Post, sound: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(androidx.compose.foundation.shape.CircleShape)) {
        val s = maxWidth
        val wf = s / p.cd
        val hf = wf * (p.vh.toFloat() / p.vw)
        val left = s / 2 - wf * p.cx
        val top = s / 2 - hf * p.cy
        LoopVideo(
            p.vid,
            Modifier.offset(left, top).wrapContentSize(Alignment.TopStart, unbounded = true).size(wf, hf),
            sound,
        )
    }
}
