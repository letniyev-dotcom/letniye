package app.letniye

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/** Островок ввода: «+», поле «Заметка», справа гс ↔ кружок, а при тексте — «отправить». */
@Composable
fun Island(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    var text by rememberSaveable { mutableStateOf("") }
    var circle by rememberSaveable { mutableStateOf(false) }
    val has = text.isNotBlank()
    // почти белая плотная заливка в светлой теме, плотная — в тёмной
    val fill = if (c.light) Color(0xFFFFFEFA) else c.island

    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IslandBtn(R.drawable.ic_add, c.text, fill) {}
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(fill)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = ts(400, 16f, 22f, c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) T("Заметка", ts(400, 16f, 22f, c.text2))
                        inner()
                    }
                },
            )
        }
        IslandBtn(
            id = if (has) R.drawable.ic_send else if (circle) R.drawable.ic_rec else R.drawable.ic_mic,
            tint = if (!has && circle) c.primary else c.text,
            fill = fill,
        ) {
            if (has) text = "" else circle = !circle
        }
    }
}

@Composable
private fun IslandBtn(@DrawableRes id: Int, tint: Color, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Ico(id, tint, Modifier.size(24.dp))
    }
}
