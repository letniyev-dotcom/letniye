package app.letniye

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SEED = "lt0"
private val PX = min(800, 240 * 2) // 240 * s, s = 2 колонки из 4

private fun img(i: Int, w: Int, h: Int) = "https://picsum.photos/seed/$SEED$i/$w/$h"

/** Лента: две равные колонки, карточка занимает колонку целиком, высота своя. */
@Composable
fun Feed(state: LazyStaggeredGridState, header: HeaderState) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = state,
        modifier = Modifier.fillMaxSize().nestedScroll(header.connection),
        // 106px сверху = статус-бар + шапка 56 + 12; 96px снизу = островок + запас
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top + 68.dp, bottom = bottom + 96.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(FEED, key = { i, _ -> i }) { i, post -> FeedItem(post, i) }
    }
}

@Composable
private fun FeedItem(post: Post, i: Int) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth()) {
        when (post.t) {
            PostType.PHOTO -> PhotoCard(post, i)
            PostType.CIRCLE -> CircleCard(post, i)
            PostType.VOICE -> VoiceCard(post, i)
            PostType.ART -> ArtCard(post, i)
            PostType.MUSIC -> MusicCard(post, i)
        }
        if (SHOW_NAMES) {
            val st = ts(500, 14f, 20f, c.text2, .1f)
            T(
                post.n,
                if (post.t == PostType.CIRCLE) st.copy(textAlign = TextAlign.Center) else st,
                Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (SHOW_DATES) {
            val st = ts(400, 12f, 16f, c.text2)
            T(
                feedDate(i),
                if (post.t == PostType.CIRCLE) st.copy(textAlign = TextAlign.Center) else st,
                Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** .card:active { opacity:.9 } */
@Composable
private fun Modifier.press(src: MutableInteractionSource): Modifier {
    val pressed by src.collectIsPressedAsState()
    return graphicsLayer { alpha = if (pressed) .9f else 1f }
        .clickable(interactionSource = src, indication = null, onClick = {})
}

@Composable
private fun Pic(url: String, modifier: Modifier) {
    val c = LocalColors.current
    Box(modifier.background(c.surface)) {
        AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

// ───────────── фото ─────────────
@Composable
private fun PhotoCard(p: Post, i: Int) {
    val src = remember { MutableInteractionSource() }
    Pic(
        img(i, PX, (PX * p.r).roundToInt()),
        Modifier.fillMaxWidth().press(src).aspectRatio(1f / p.r).clip(RoundedCornerShape(12.dp)),
    )
}

// ───────────── кружочек ─────────────
@Composable
private fun CircleCard(p: Post, i: Int) {
    val c = LocalColors.current
    val src = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().press(src).aspectRatio(1f).clip(CircleShape).background(c.surface),
    ) {
        AsyncImage(img(i, 400, 400), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .16f)))
        Ico(R.drawable.ic_play_r, Color.White, Modifier.align(Alignment.Center).fillMaxSize(.3f))
        T(
            p.d,
            ts(500, 12f, 16f, Color.White),
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * .11f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = .45f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

// ───────────── голосовое ─────────────
private fun waveH(j: Int, k: Int): Float {
    val v = .22 + .78 * abs(sin((j + 1) * 1.7 + k) * cos((j + 1) * .6 + k * 2))
    return (v * 100).roundToInt() / 100f
}

@Composable
private fun VoiceCard(p: Post, i: Int) {
    val c = LocalColors.current
    val src = remember { MutableInteractionSource() }
    val n = 15
    Row(
        Modifier
            .fillMaxWidth()
            .press(src)
            .clip(RoundedCornerShape(12.dp))
            .background(c.tint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(R.drawable.ic_play, c.primary, Modifier.size(28.dp))
        Row(
            Modifier.weight(1f).height(26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (j in 0 until n) {
                Box(
                    Modifier
                        .weight(1f)
                        .widthIn(min = 2.dp)
                        .fillMaxHeight(waveH(j, i))
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (j.toFloat() / n < p.pr) c.primary else c.wave),
                )
            }
        }
        T(p.d, ts(500, 12f, 16f, c.text2))
    }
}

// ───────────── музыка ─────────────
private val NO_COVER = Color(0xFF6B6B60)

/** Средний насыщенный цвет обложки: тусклые и пересвеченные пиксели весят меньше. */
private fun coverColor(bmp: Bitmap): Color {
    val n = 24
    val px = IntArray(n * n)
    Bitmap.createScaledBitmap(bmp, n, n, true).getPixels(px, 0, n, 0, 0, n, n)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var w = 0.0
    for (v in px) {
        val pr = (v shr 16) and 255
        val pg = (v shr 8) and 255
        val pb = v and 255
        val mx = maxOf(pr, pg, pb)
        val mn = minOf(pr, pg, pb)
        val sat = (mx - mn) / (if (mx == 0) 1 else mx).toDouble()
        val l = mx / 255.0
        val wt = .05 + sat * sat * (1 - abs(l - .55))
        r += pr * wt
        g += pg * wt
        b += pb * wt
        w += wt
    }
    return Color((r / w / 255).toFloat(), (g / w / 255).toFloat(), (b / w / 255).toFloat())
}

/** Слой блюра: та же обложка, размытая и проявленная маской от `from` до `to` высоты карточки. */
@Composable
private fun BoxScope.BlurLayer(req: ImageRequest, radius: Dp, from: Float, to: Float) {
    val mask = Brush.verticalGradient(
        colorStops = arrayOf(0f to Color.Transparent, from to Color.Transparent, to to Color.Black, 1f to Color.Black),
    )
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            },
    ) {
        AsyncImage(
            req,
            null,
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
                .blur(radius, BlurredEdgeTreatment.Unbounded),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Обложка на всю карточку 4:5, низ растворяется прогрессивным блюром (3 слоя) в тёмный оттенок
 * обложки, текст белый. Без объёма: ни кромки, ни тени. Блюр работает с Android 12 (API 31),
 * на старых версиях остаётся резкая обложка под тем же затемнением.
 */
@Composable
private fun MusicCard(p: Post, i: Int) {
    val src = remember { MutableInteractionSource() }
    val ctx = LocalContext.current
    val url = img(i, PX, (PX * 1.25f).roundToInt())
    val req = remember(url) { ImageRequest.Builder(ctx).data(url).allowHardware(false).build() }
    var base by remember(url) { mutableStateOf(NO_COVER) }
    val dark = lerp(base, Color.Black, .45f)
    Box(
        Modifier
            .fillMaxWidth()
            .press(src)
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(26.dp))
            .background(base),
    ) {
        AsyncImage(
            req,
            null,
            Modifier.matchParentSize(),
            onSuccess = { st ->
                (st.result.drawable as? BitmapDrawable)?.bitmap?.let { bmp ->
                    runCatching { coverColor(bmp) }.getOrNull()?.let { base = it }
                }
            },
            contentScale = ContentScale.Crop,
        )
        BlurLayer(req, 6.dp, .30f, .55f)
        BlurLayer(req, 14.dp, .45f, .72f)
        BlurLayer(req, 28.dp, .58f, .88f)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        .42f to Color.Transparent,
                        .72f to dark.copy(alpha = .55f),
                        1f to dark.copy(alpha = .92f),
                    ),
                ),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                T(p.ti, ts(500, 16f, 22f, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                T(p.ar, ts(400, 13f, 18f, Color.White.copy(alpha = .75f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Ico(R.drawable.ic_play, Color.White, Modifier.size(34.dp))
        }
    }
}

// ───────────── заметка: объёмное стекло, подкрашенное цветом заметки ─────────────
@Composable
private fun ArtCard(p: Post, i: Int) {
    val c = LocalColors.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Column(Modifier.fillMaxWidth().press(src).glass(c, 26.dp, pressed, p.col.c)) {
        if (p.cv) {
            Pic(
                img(i, PX, PX),
                Modifier
                    .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
        Column(
            Modifier.padding(
                if (p.cv) PaddingValues(16.dp, 12.dp, 16.dp, 16.dp) else PaddingValues(18.dp, 18.dp, 18.dp, 20.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(
                p.h,
                if (p.cv) ts(500, 16f, 22f, c.text, .1f) else ts(500, 18f, 24f, c.text, .1f),
            )
            T(p.p, ts(400, 14f, 20f, c.text2), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Светлая тема: градиент 150° (серый → белый) с лёгким оттенком цвета заметки n (17% → 4%),
 * мягкая тень цвета заметки, внутреннее свечение 14px, светящаяся кромка 1px (135°);
 * при нажатии кромка 2px цвета темы.
 * Тёмная тема: плоская заливка с оттенком n (15%), без тени, свечения и кромки.
 */
private fun Modifier.glass(c: LColors, radius: Dp, pressed: Boolean, n: Color): Modifier = drawWithCache {
    val r = radius.toPx()
    val w = size.width
    val h = size.height
    val corner = CornerRadius(r)
    if (!c.light) {
        onDrawWithContent {
            drawRoundRect(lerp(c.surface, n, .15f), cornerRadius = corner)
            drawContent()
        }
    } else {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.ga.toArgb()
            setShadowLayer(14.dp.toPx(), 0f, 6.dp.toPx(), n.copy(alpha = .16f).toArgb())
        }
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, corner)) }
        val m = 40.dp.toPx()
        val frame = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(-m, -m, w + m, h + m))
            addRoundRect(RoundRect(0f, 0f, w, h, corner))
        }.asAndroidPath()
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.gglow.toArgb()
            maskFilter = BlurMaskFilter(11.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val bg = cssBrush(size, 150f, 0f to lerp(c.ga, n, .17f), 1f to lerp(c.gb, n, .04f))
        val rim = if (pressed) {
            cssBrush(size, 160f, 0f to c.primary, .7f to Color(0xFF74AE2E).copy(alpha = .15f), 1f to c.primary)
        } else {
            cssBrush(size, 135f, 0f to c.rimHi, .38f to c.rimLo, .62f to c.rimLo, 1f to c.rimHi)
        }
        val rw = (if (pressed) 2.dp else 1.dp).toPx()
        onDrawWithContent {
            drawIntoCanvas { cv -> cv.nativeCanvas.drawRoundRect(0f, 0f, w, h, r, r, shadow) }
            drawRoundRect(bg, cornerRadius = corner)
            clipPath(clip) { drawIntoCanvas { cv -> cv.nativeCanvas.drawPath(frame, glow) } }
            drawContent()
            drawRoundRect(
                rim,
                topLeft = Offset(rw / 2, rw / 2),
                size = Size(w - rw, h - rw),
                cornerRadius = CornerRadius(r - rw / 2),
                style = Stroke(rw),
            )
        }
    }
}
