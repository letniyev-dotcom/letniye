package app.letniye

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Кривая и тайминги из ChatScreen второго проекта (TgDefault, INK_MS).
private val Ease = CubicBezierEasing(0.19919473f, 0.010644531f, 0.27920938f, 0.9102539f)
private const val INK_MS = 200f
private const val MAX_LINES = 10

/**
 * Островок ввода. В покое — три отдельных островка: «+», поле, кнопка гс/кружок.
 * Как только в поле есть текст, они сливаются в одну пилюлю (как в Telegram):
 * «+» остаётся слева внутри, а справа — круглая кнопка «отправить».
 * Размеры, шрифт 18sp, свои буквы с проявлением и своя каретка — из второго проекта.
 */
@Composable
fun Island(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var circle by rememberSaveable { mutableStateOf(false) }
    val has = input.text.isNotBlank()
    // почти белая плотная заливка в светлой теме, плотная — в тёмной
    val fill = if (c.light) Color(0xFFFFFEFA) else c.island

    // 0 — три островка, 1 — одна пилюля
    val merge = animateFloatAsState(if (has) 1f else 0f, tween(250, easing = Ease), label = "merge")
    val gap = (8f * (1f - merge.value)).dp
    val focus = remember { FocusRequester() }

    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            // поля пилюли в чате: 8 по бокам, 6 сверху, 8 снизу
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // общая пилюля проявляется под островками и закрывает щели между ними
                .drawBehind {
                    val a = merge.value
                    if (a > 0f) drawRoundRect(fill, size = size, cornerRadius = CornerRadius(26.dp.toPx()), alpha = a)
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            // «+»
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) { Ico(R.drawable.ic_add, c.text, Modifier.size(24.dp)) }

            // поле: растёт вверх, вровень с кнопками по нижнему краю
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(fill)
                    .animateContentSize(tween(250, easing = Ease))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        focus.requestFocus()
                    }
                    // слитая пилюля: текст идёт сразу за «+» и до кнопки отправки
                    .padding(start = (16f - 14f * merge.value).dp, end = (16f - 14f * merge.value).dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IslandField(input, { input = it }, c, focus)
            }

            // гс ↔ кружок, а при тексте — «отправить»
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (has) input = TextFieldValue("") else circle = !circle
                    },
                contentAlignment = Alignment.Center,
            ) {
                // круглая кнопка отправки 40dp внутри пилюли
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            alpha = merge.value
                            val s = 0.75f + 0.25f * merge.value
                            scaleX = s; scaleY = s
                        }
                        .background(c.primary, CircleShape)
                )
                // смена иконки с масштабом и фейдом, как replaceIcon в TG
                AnimatedContent(
                    targetState = if (has) 0 else if (circle) 2 else 1,
                    transitionSpec = {
                        (fadeIn(tween(150, easing = Ease)) + scaleIn(initialScale = 0.4f, animationSpec = tween(150, easing = Ease)))
                            .togetherWith(fadeOut(tween(120, easing = Ease)) + scaleOut(targetScale = 0.4f, animationSpec = tween(120, easing = Ease)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "sendIcon",
                ) { st ->
                    when (st) {
                        0 -> Ico(R.drawable.ic_send, c.onPrimary, Modifier.size(24.dp))
                        2 -> Ico(R.drawable.ic_rec, c.primary, Modifier.size(24.dp))
                        else -> Ico(R.drawable.ic_mic, c.text, Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/**
 * Само поле: 18sp, до 10 строк с внутренним скроллом, свои буквы (проявляются из-под строки
 * с подъёмом 2dp за 200мс) и своя каретка — скруглённая, едет пружиной, мягко мигает.
 * Системный текст и каретка прозрачны: рисуем их сами, выделение и подсказки рисует система.
 */
@Composable
private fun IslandField(
    input: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    c: LColors,
    focus: FocusRequester,
) {
    var fieldLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }

    // очередь «индекс буквы → когда начала проявляться»
    val inkStarts = remember { java.util.ArrayDeque<LongArray>() }
    val inkFrame = remember { mutableLongStateOf(0L) }
    var inkKick by remember { mutableIntStateOf(0) }
    LaunchedEffect(inkKick) {
        while (inkStarts.isNotEmpty()) {
            withFrameMillis { }
            val now = android.os.SystemClock.uptimeMillis()
            while (inkStarts.isNotEmpty() && now - inkStarts.first()[1] >= INK_MS) inkStarts.removeFirst()
            inkFrame.longValue = now
        }
    }

    // каретка: не прыгает по буквам, а едет за ними
    val caretA = remember { Animatable(1f) }
    val caretX = remember { Animatable(0f) }
    val caretY = remember { Animatable(0f) }
    val caretH = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(input.selection.end, input.text, fieldLayout) {
        val tl = fieldLayout ?: return@LaunchedEffect
        val cr = tl.getCursorRect(input.selection.end.coerceIn(0, tl.layoutInput.text.length))
        val first = caretH.floatValue == 0f
        caretH.floatValue = cr.height
        if (first) {
            caretX.snapTo(cr.left); caretY.snapTo(cr.top)
        } else {
            // пружина без перелёта: трогается мягко и так же мягко причаливает
            launch { caretX.animateTo(cr.left, spring(dampingRatio = 1f, stiffness = 1300f, visibilityThreshold = 0.5f)) }
            launch { caretY.animateTo(cr.top, spring(dampingRatio = 1f, stiffness = 700f, visibilityThreshold = 0.5f)) }
        }
    }
    LaunchedEffect(input.text, input.selection, fieldFocused) {
        if (!fieldFocused) { caretA.snapTo(0f); return@LaunchedEffect }
        // пока печатаешь — каретка ровная, мигать начинает в паузе
        caretA.snapTo(1f)
        kotlinx.coroutines.delay(480L)
        while (true) {
            caretA.animateTo(0.1f, tween(430, easing = Ease))
            caretA.animateTo(1f, tween(430, easing = Ease))
            kotlinx.coroutines.delay(120L)
        }
    }

    // свой скролл: поле измеряется без предела по высоте, а видимая часть режется по 10 строкам
    val fieldScroll = remember { mutableFloatStateOf(0f) }
    val fieldMaxScroll = remember { mutableFloatStateOf(0f) }
    val fieldCursorKey = remember { intArrayOf(Int.MIN_VALUE) }

    Box(
        Modifier
            .scrollable(
                rememberScrollableState { d ->
                    val old = fieldScroll.floatValue
                    val new = (old - d).coerceIn(0f, fieldMaxScroll.floatValue)
                    fieldScroll.floatValue = new
                    old - new
                },
                Orientation.Vertical,
            )
            .layout { m, cs ->
                val p = m.measure(cs.copy(maxHeight = Constraints.Infinity))
                val tl = fieldLayout
                val padV = 13.dp.toPx()
                val h = if (tl != null && tl.lineCount > 0) {
                    val lineH = tl.getLineBottom(0) - tl.getLineTop(0)
                    p.height.coerceAtMost((padV * 2 + lineH * MAX_LINES).roundToInt())
                } else p.height
                var sc = fieldScroll.floatValue
                val key = input.selection.end * 31 + input.text.length
                if (tl != null && key != fieldCursorKey[0]) {
                    fieldCursorKey[0] = key
                    // держим курсор в видимой полосе
                    val cr = tl.getCursorRect(input.selection.end.coerceIn(0, tl.layoutInput.text.length))
                    if (cr.bottom + padV - sc > h - padV) sc = cr.bottom + padV - (h - padV)
                    if (cr.top < sc) sc = cr.top
                }
                sc = sc.coerceIn(0f, (p.height - h).coerceAtLeast(0).toFloat())
                fieldScroll.floatValue = sc
                fieldMaxScroll.floatValue = (p.height - h).coerceAtLeast(0).toFloat()
                layout(p.width, h) { p.place(0, -sc.roundToInt()) }
            },
    ) {
        BasicTextField(
            value = input,
            onValueChange = {
                val was = input.text
                if (it.text != was) {
                    // дописали в конец — проявляем добавленное; правка в середине или вставка — сразу
                    if (it.text.length > was.length && it.text.startsWith(was)) {
                        val t0 = android.os.SystemClock.uptimeMillis()
                        var k = 0
                        for (i in was.length until it.text.length) {
                            inkStarts.addLast(longArrayOf(i.toLong(), t0 + k * 12L)); k++
                        }
                        // больше десяти букв в полёте не держим
                        while (inkStarts.size > 10) inkStarts.removeFirst()
                        inkKick++
                    } else inkStarts.clear()
                }
                onChange(it)
            },
            onTextLayout = { fieldLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
                .focusRequester(focus)
                .onFocusChanged { fieldFocused = it.isFocused }
                .drawWithContent {
                    // сам текст поля прозрачный — рисуем его здесь: буквы выходят из-под строки,
                    // с подъёмом и проявлением
                    drawContent()
                    val tl = fieldLayout ?: return@drawWithContent
                    val ink = c.text
                    val n = tl.layoutInput.text.length
                    inkFrame.longValue // подписка на кадры
                    val now = android.os.SystemClock.uptimeMillis()
                    val rise = 2.dp.toPx()
                    var firstAnim = n
                    for (e in inkStarts) {
                        val i = e[0].toInt()
                        if (i < n && now - e[1] < INK_MS) { firstAnim = i; break }
                    }
                    if (n > 0) {
                        if (firstAnim >= n) drawText(tl, color = ink)
                        else {
                            if (firstAnim > 0) {
                                val ln = tl.getLineForOffset(firstAnim)
                                if (ln > 0) clipRect(0f, 0f, size.width, tl.getLineTop(ln)) { drawText(tl, color = ink) }
                                clipRect(0f, tl.getLineTop(ln), tl.getBoundingBox(firstAnim).left, tl.getLineBottom(ln)) {
                                    drawText(tl, color = ink)
                                }
                            }
                            for (e in inkStarts) {
                                val i = e[0].toInt()
                                if (i >= n) continue
                                val pr = ((now - e[1]) / INK_MS).coerceIn(0f, 1f)
                                val ln = tl.getLineForOffset(i)
                                val bx = tl.getBoundingBox(i)
                                clipRect(bx.left, tl.getLineTop(ln), bx.right, tl.getLineBottom(ln)) {
                                    translate(0f, (1f - Ease.transform(pr)) * rise) {
                                        drawText(tl, color = ink, alpha = pr)
                                    }
                                }
                            }
                        }
                    }
                    // своя каретка: скруглённая 2dp, чуть выше строки, мягко мигает
                    if (fieldFocused && input.selection.collapsed) {
                        val cw = 2.dp.toPx()
                        val over = 1.5.dp.toPx()
                        drawRoundRect(
                            c.primary,
                            topLeft = Offset(caretX.value, caretY.value - over),
                            size = Size(cw, (caretH.floatValue + over * 2).coerceAtLeast(cw)),
                            cornerRadius = CornerRadius(cw / 2f),
                            alpha = caretA.value,
                        )
                    }
                },
            // в чате 18sp; строка 22sp даёт ровно 13+22+13 = 48dp высоты пилюли
            textStyle = ts(400, 18f, 22f, Color.Transparent),
            // системная каретка выключена — рисуем свою
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = { inner ->
                Box {
                    if (input.text.isEmpty()) T("Заметка", ts(400, 18f, 22f, c.text2))
                    inner()
                }
            },
        )
    }
}
