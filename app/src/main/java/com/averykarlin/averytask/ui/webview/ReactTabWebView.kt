package com.averykarlin.averytask.ui.webview

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.averykarlin.averytask.data.preferences.ThemePreferences
import com.averykarlin.averytask.data.repository.HabitRepository
import com.averykarlin.averytask.data.repository.ProjectRepository
import com.averykarlin.averytask.data.repository.TagRepository
import com.averykarlin.averytask.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReactTabWebView(
    tabName: String,
    taskRepository: TaskRepository,
    projectRepository: ProjectRepository,
    habitRepository: HabitRepository,
    tagRepository: TagRepository,
    themePreferences: ThemePreferences,
    onNavigate: (String) -> Unit,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bridge = remember(tabName) {
        WebViewBridge(
            taskRepository = taskRepository,
            projectRepository = projectRepository,
            habitRepository = habitRepository,
            tagRepository = tagRepository,
            themePreferences = themePreferences,
            onNavigate = onNavigate,
            scope = scope
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                addJavascriptInterface(bridge, "AndroidBridge")
                loadUrl("file:///android_asset/web/index.html#$tabName")
            }
        },
        update = { webView ->
            // When the tab changes, switch the hash route
            webView.evaluateJavascript(
                "if(window.setActiveTab) window.setActiveTab('$tabName'); else window.location.hash='$tabName';",
                null
            )
        }
    )
}
