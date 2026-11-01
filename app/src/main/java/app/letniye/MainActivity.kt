package app.letniye

import android.app.Activity
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { App() }
    }
}

@Composable
fun App() {
    // светлая тема по умолчанию; переключение — долгим нажатием на логотип
    var light by rememberSaveable { mutableStateOf(true) }
    val colors = if (light) LightColors else DarkColors

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    CompositionLocalProvider(LocalColors provides colors) {
        val grid = rememberLazyStaggeredGridState()
        val header = rememberHeaderState(grid)
        val scope = rememberCoroutineScope()
        // лента живая: новые кружки добавляются сверху
        val posts = remember { mutableStateListOf<Post>().apply { addAll(FEED.mapIndexed { i, p -> p.copy(id = i, k = i) }) } }
        Box(Modifier.fillMaxSize().background(colors.bg)) {
            Feed(grid, header, posts)
            TopFade(header, Modifier.align(Alignment.TopCenter))
            BottomFade(Modifier.align(Alignment.BottomCenter))
            Header(header) { light = !light }
            Island(
                Modifier.align(Alignment.BottomCenter),
                onVideo = { v ->
                    posts.add(
                        0,
                        Post(
                            PostType.CIRCLE, "Илья", d = fmtSec(v.sec), id = 1000 + posts.size,
                            vid = v.path, vw = v.w, vh = v.h, cx = v.cx, cy = v.cy, cd = v.cd,
                        ),
                    )
                    scope.launch { grid.animateScrollToItem(0) }
                },
            )
        }
    }
}
