package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private class LiftStreamSnifferBridge(
    private val onStreamDetected: (String) -> Unit,
) {
    @JavascriptInterface
    fun onVideoUrlFound(url: String?) {
        val clean = url?.trim().orEmpty()
        if (clean.startsWith("http", ignoreCase = true) && isPlayableStreamUrl(clean)) {
            onStreamDetected(clean)
        }
    }
}

private fun isPlayableStreamUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains(".ts") && !lower.contains(".m3u8")) return false
    if (lower.contains(".m4s") || lower.contains(".vtt") || lower.contains(".srt")) return false
    return lower.contains(".m3u8") ||
        lower.contains(".mpd") ||
        lower.contains(".mp4") ||
        lower.contains(".webm") ||
        lower.contains(".mkv")
}

private val LIFT_SPA_SNIFFER_JS = """
(function() {
    if (window.__liftSnifferInstalled) {
        if (window.__liftScanDom) window.__liftScanDom();
        return;
    }
    window.__liftSnifferInstalled = true;

    function checkUrl(u) {
        if (!u || typeof u !== 'string') return;
        if (u.indexOf('http') !== 0) return;
        var l = u.toLowerCase();
        if (l.indexOf('.m3u8') !== -1 || l.indexOf('.mpd') !== -1 || l.indexOf('.mp4') !== -1 || l.indexOf('.webm') !== -1 || l.indexOf('.mkv') !== -1) {
            LiftSniffer.onVideoUrlFound(u);
        }
    }

    window.__liftScanDom = function() {
        var vids = document.querySelectorAll('video, video source');
        for (var i = 0; i < vids.length; i++) {
            var s = vids[i].currentSrc || vids[i].src || vids[i].getAttribute('data-src');
            if (s && s.indexOf('http') === 0) {
                LiftSniffer.onVideoUrlFound(s);
            }
        }
    };

    if (window.fetch) {
        var origFetch = window.fetch;
        window.fetch = function() {
            try {
                var arg = arguments[0];
                var url = typeof arg === 'string' ? arg : (arg && arg.url);
                checkUrl(url);
            } catch (e) {}
            return origFetch.apply(this, arguments);
        };
    }

    if (window.XMLHttpRequest) {
        var origOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function(method, url) {
            try { checkUrl(url); } catch (e) {}
            return origOpen.apply(this, arguments);
        };
    }

    if (window.MutationObserver && document.documentElement) {
        new MutationObserver(function() {
            window.__liftScanDom();
        }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
    }

    setInterval(window.__liftScanDom, 1500);
    window.__liftScanDom();
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiftBrowserScreen(
    initialUrl: String,
    onBack: () -> Unit,
    onStartPartyWithStream: (title: String, pageUrl: String, streamUrl: String) -> Unit,
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var currentTitle by remember { mutableStateOf("Lift Explorer") }
    var isPageLoading by remember { mutableStateOf(true) }
    val detectedStreams = remember { mutableStateListOf<String>() }
    var selectedStreamIndex by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun recordStream(url: String) {
        if (url !in detectedStreams) {
            val isMaster = url.contains(".m3u8", ignoreCase = true) || url.contains(".mpd", ignoreCase = true)
            if (isMaster) {
                detectedStreams.add(0, url)
                selectedStreamIndex = 0
            } else {
                detectedStreams.add(url)
            }
        }
    }

    BackHandler {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("browser_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reload Lift page",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (isPageLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.allowContentAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        addJavascriptInterface(
                            LiftStreamSnifferBridge { stream ->
                                post { recordStream(stream) }
                            },
                            "LiftSniffer"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) currentTitle = title
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isPageLoading = true
                                if (!url.isNullOrBlank()) currentUrl = url
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString().orEmpty()
                                if (reqUrl.startsWith("http", ignoreCase = true) && isPlayableStreamUrl(reqUrl)) {
                                    post { recordStream(reqUrl) }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                if (!url.isNullOrBlank()) currentUrl = url
                                view?.evaluateJavascript(LIFT_SPA_SNIFFER_JS, null)
                            }
                        }

                        webViewRef = this
                        loadUrl(initialUrl)
                    }
                },
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = detectedStreams.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                val safeIndex = selectedStreamIndex.coerceIn(0, (detectedStreams.size - 1).coerceAtLeast(0))
                val primaryStream = detectedStreams.getOrNull(safeIndex).orEmpty()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stream Detected (${detectedStreams.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = primaryStream,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = {
                                    onStartPartyWithStream(currentTitle, currentUrl, primaryStream)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("browser_start_party_button"),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Party")
                            }
                        }

                        if (detectedStreams.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(detectedStreams.take(6)) { index, url ->
                                    val selected = index == safeIndex
                                    val ext = when {
                                        url.contains(".m3u8", true) -> "HLS"
                                        url.contains(".mpd", true) -> "DASH"
                                        else -> "VIDEO"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { selectedStreamIndex = index }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = "#${index + 1} $ext",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
