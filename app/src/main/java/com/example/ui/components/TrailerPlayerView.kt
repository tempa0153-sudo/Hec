package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TrailerResolutionInfo
import com.example.data.remote.StremioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TrailerPlaybackMethod(val label: String) {
    METHOD_A("A (YouTube Embed)"),
    METHOD_B("B (Direct Stream)"),
    METHOD_C("C (External YouTube)")
}

private class TrailerJsBridge(
    private val onReady: () -> Unit,
    private val onError: (Int) -> Unit,
    private val onTimeUpdate: (Float, Float) -> Unit,
    private val onStateChange: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post { onReady() }
    }

    @JavascriptInterface
    fun onPlayerError(code: Int) {
        mainHandler.post { onError(code) }
    }

    @JavascriptInterface
    fun onTimeUpdate(curr: Float, dur: Float) {
        mainHandler.post { onTimeUpdate(curr, dur) }
    }

    @JavascriptInterface
    fun onStateChange(state: Int) {
        mainHandler.post { onStateChange(state) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayerView(
    youtubeId: String?,
    trailerIds: List<String> = emptyList(),
    backdropUrl: String?,
    movieTitle: String,
    imdbId: String = "",
    year: String? = null,
    type: String = "movie",
    trailerResolution: TrailerResolutionInfo? = null,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    showDebugLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeMethod by remember { mutableStateOf(TrailerPlaybackMethod.METHOD_A) }
    var currentTrailerId by remember(youtubeId) { mutableStateOf(youtubeId?.trim().takeIf { !it.isNullOrBlank() }) }
    var idSource by remember(trailerResolution) { mutableStateOf(trailerResolution?.source ?: "none") }
    var tmdbHttpStatus by remember(trailerResolution) { mutableStateOf(trailerResolution?.tmdbHttpStatus) }
    var playerStateName by remember { mutableStateOf("UNSTARTED") }
    var lastErrorCode by remember { mutableStateOf("none") }
    var directStreamUrl by remember { mutableStateOf<String?>(null) }

    var currentPositionSec by remember { mutableFloatStateOf(0f) }
    var totalDurationSec by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var scrubPositionSec by remember { mutableFloatStateOf(0f) }
    var showPlayPauseIndicator by remember { mutableStateOf(false) }

    // Resolve trailer ID if missing
    LaunchedEffect(movieTitle, imdbId, currentTrailerId) {
        if (currentTrailerId.isNullOrBlank() && (imdbId.isNotBlank() || movieTitle.isNotBlank())) {
            withContext(Dispatchers.IO) {
                Log.d("TrailerPlayer", "[Resolution] Resolving trailer for '$movieTitle' (IMDb: $imdbId)")
                val info = StremioClient().resolveTrailerDetailed(
                    imdbId = imdbId,
                    title = movieTitle,
                    year = year,
                    type = type
                )
                withContext(Dispatchers.Main) {
                    if (!info.youtubeId.isNullOrBlank()) {
                        currentTrailerId = info.youtubeId
                        idSource = info.source
                        tmdbHttpStatus = info.tmdbHttpStatus
                        Log.d("TrailerPlayer", "[Resolution] Resolved YouTube ID: ${info.youtubeId} via ${info.source}")
                    } else {
                        idSource = "none"
                        tmdbHttpStatus = info.tmdbHttpStatus
                        Log.d("TrailerPlayer", "[Resolution] No trailer ID found. Area will be hidden cleanly.")
                    }
                }
            }
        }
    }

    // Step 2 Watchdog timer for Method A:
    // If state doesn't reach PLAYING within 5 seconds, attempt unmuted -> muted or fallback to Method B
    LaunchedEffect(currentTrailerId, activeMethod) {
        if (activeMethod == TrailerPlaybackMethod.METHOD_A && !currentTrailerId.isNullOrBlank()) {
            delay(5000)
            if (!isPlaying && activeMethod == TrailerPlaybackMethod.METHOD_A) {
                Log.d("TrailerPlayer", "[Method A] 5s timeout reached without PLAYING state. Attempting muted playback first...")
                lastErrorCode = "5s_timeout"
                // Try muted playback if unmuted was blocked by autoplay policies
                if (!isMuted) {
                    webViewInstance?.evaluateJavascript("sendCmd('mute'); sendCmd('playVideo');", null)
                    delay(2000)
                }
                if (!isPlaying && activeMethod == TrailerPlaybackMethod.METHOD_A) {
                    Log.d("TrailerPlayer", "[Method A] Fallback triggered -> Transitioning to Method B (Direct stream)")
                    activeMethod = TrailerPlaybackMethod.METHOD_B
                }
            }
        }
    }

    // Method B handler: query Piped instances for progressive MP4 stream
    LaunchedEffect(activeMethod, currentTrailerId) {
        if (activeMethod == TrailerPlaybackMethod.METHOD_B && !currentTrailerId.isNullOrBlank()) {
            isLoading = true
            playerStateName = "FETCHING_DIRECT_STREAM"
            Log.d("TrailerPlayer", "[Method B] Racing Piped instances for 4s on ID: $currentTrailerId")
            withContext(Dispatchers.IO) {
                val stream = StremioClient().racePipedDirectStream(currentTrailerId!!)
                withContext(Dispatchers.Main) {
                    if (!stream.isNullOrBlank()) {
                        directStreamUrl = stream
                        playerStateName = "DIRECT_STREAM_READY"
                        Log.d("TrailerPlayer", "[Method B] Direct stream obtained: $stream")
                    } else {
                        Log.d("TrailerPlayer", "[Method B] All Piped instances failed/timed out -> Transitioning to Method C (YouTube Button)")
                        lastErrorCode = "piped_timeout"
                        activeMethod = TrailerPlaybackMethod.METHOD_C
                        isLoading = false
                    }
                }
            }
        }
    }

    // Sync mute state changes to the active player
    LaunchedEffect(isMuted, webViewInstance) {
        val cmd = if (isMuted) "sendCmd('mute');" else "sendCmd('unMute');"
        webViewInstance?.evaluateJavascript(cmd, null)
    }

    // Pause on lifecycle pause/stop and resume on resume
    DisposableEffect(lifecycleOwner, webViewInstance) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    try {
                        webViewInstance?.evaluateJavascript("sendCmd('pauseVideo');", null)
                        webViewInstance?.onPause()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_RESUME -> {
                    try {
                        webViewInstance?.onResume()
                    } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Clean disposal of WebView on exit
    DisposableEffect(currentTrailerId) {
        onDispose {
            try {
                webViewInstance?.evaluateJavascript("sendCmd('stopVideo');", null)
                webViewInstance?.stopLoading()
                webViewInstance?.loadUrl("about:blank")
                webViewInstance?.destroy()
                webViewInstance = null
            } catch (_: Exception) {}
        }
    }

    fun seekTo(seconds: Float) {
        webViewInstance?.evaluateJavascript("sendCmd('seekTo', [$seconds, true]);", null)
    }

    fun togglePlayPause() {
        showPlayPauseIndicator = true
        if (isPlaying) {
            isPlaying = false
            webViewInstance?.evaluateJavascript("sendCmd('pauseVideo');", null)
        } else {
            isPlaying = true
            webViewInstance?.evaluateJavascript("sendCmd('playVideo');", null)
        }
    }

    LaunchedEffect(showPlayPauseIndicator) {
        if (showPlayPauseIndicator) {
            delay(700)
            showPlayPauseIndicator = false
        }
    }

    // STEP 1 d) If no trailer is found, hide the trailer area cleanly with no error UI!
    if (currentTrailerId.isNullOrBlank()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trailer_player_container")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .testTag("trailer_player_view")
        ) {
            // Immediate Backdrop Image: displays instantly so screen is never pitch black
            if (!backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .size(640, 360)
                        .allowRgb565(true)
                        .crossfade(true)
                        .build(),
                    contentDescription = movieTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val muteParam = if (isMuted) 1 else 0

            when (activeMethod) {
                TrailerPlaybackMethod.METHOD_A -> {
                    // STEP 2 A) YouTube IFrame Player API embed:
                    // - src = https://www.youtube-nocookie.com/embed/{id}?enablejsapi=1&controls=0&autoplay=1&playsinline=1&rel=0&modestbranding=1&origin=https://www.youtube.com
                    // - iframe attributes: referrerpolicy="strict-origin" and allow="autoplay; encrypted-media; picture-in-picture"
                    // - origin = https://www.youtube.com with loadDataWithBaseURL("https://www.youtube.com/", ...)
                    // - mediaPlaybackRequiresUserGesture = false
                    val embedHtml = remember(currentTrailerId, muteParam) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                          <style>
                            * { margin:0; padding:0; box-sizing:border-box; background:#000000; overflow:hidden; }
                            html, body { width:100%; height:100%; background:#000000; }
                            .video-wrapper { position:relative; width:100%; height:100%; background:#000000; }
                            iframe {
                              position:absolute; top:0; left:0; width:100%; height:100%; border:none; pointer-events:none;
                            }
                          </style>
                        </head>
                        <body>
                          <div class="video-wrapper">
                            <iframe id="trailerPlayer"
                              src="https://www.youtube-nocookie.com/embed/$currentTrailerId?enablejsapi=1&controls=0&autoplay=1&playsinline=1&rel=0&modestbranding=1&mute=$muteParam&iv_load_policy=3&disablekb=1&fs=0&origin=https://www.youtube.com"
                              referrerpolicy="strict-origin"
                              allow="autoplay; encrypted-media; picture-in-picture"
                              allowfullscreen>
                            </iframe>
                          </div>
                          <script>
                            var frame = document.getElementById('trailerPlayer');
                            function sendCmd(func, args) {
                              if (frame && frame.contentWindow) {
                                frame.contentWindow.postMessage(JSON.stringify({
                                  'event': 'command',
                                  'func': func,
                                  'args': args || []
                                }), '*');
                              }
                            }
                            window.addEventListener('message', function(e) {
                              try {
                                var msg = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
                                if (!msg) return;
                                if (msg.event === 'onReady') {
                                  sendCmd('playVideo');
                                  if (window.AndroidBridge) window.AndroidBridge.onPlayerReady();
                                } else if (msg.event === 'infoDelivery' && msg.info) {
                                  var curr = msg.info.currentTime;
                                  var dur = msg.info.duration;
                                  var state = msg.info.playerState;
                                  if (state !== undefined && window.AndroidBridge) {
                                    window.AndroidBridge.onStateChange(state);
                                  }
                                  if (curr !== undefined && dur !== undefined && window.AndroidBridge) {
                                    window.AndroidBridge.onTimeUpdate(curr, dur);
                                  }
                                } else if (msg.event === 'onError' && window.AndroidBridge) {
                                  window.AndroidBridge.onPlayerError(msg.info || 0);
                                }
                              } catch(err) {}
                            });
                            function initListening() {
                              if (frame && frame.contentWindow) {
                                frame.contentWindow.postMessage(JSON.stringify({
                                  'event': 'listening',
                                  'id': 'trailerPlayer'
                                }), '*');
                                sendCmd('playVideo');
                              }
                            }
                            frame.onload = function() {
                              initListening();
                              setTimeout(initListening, 400);
                              setTimeout(initListening, 1200);
                            };
                          </script>
                        </body>
                        </html>
                        """.trimIndent()
                    }

                    androidx.compose.runtime.key(currentTrailerId) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.allowContentAccess = true
                                    settings.allowFileAccess = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    addJavascriptInterface(
                                        TrailerJsBridge(
                                            onReady = {
                                                Log.d("TrailerPlayer", "[Method A] Player onReady")
                                                playerStateName = "READY"
                                            },
                                            onError = { errorCode ->
                                                Log.d("TrailerPlayer", "[Method A] Player onError: $errorCode")
                                                lastErrorCode = errorCode.toString()
                                                // 101/150/152/153 are embed restriction codes -> fallback to Method B
                                                if (errorCode in listOf(101, 150, 152, 153)) {
                                                    Log.d("TrailerPlayer", "[Method A] Embed restriction ($errorCode) -> Fallback to Method B")
                                                    activeMethod = TrailerPlaybackMethod.METHOD_B
                                                }
                                            },
                                            onTimeUpdate = { curr, dur ->
                                                currentPositionSec = curr
                                                if (dur > 0f) totalDurationSec = dur
                                                if (curr > 0f) isLoading = false
                                            },
                                            onStateChange = { state ->
                                                isPlaying = (state == 1)
                                                playerStateName = when (state) {
                                                    -1 -> "UNSTARTED"
                                                    0 -> "ENDED"
                                                    1 -> "PLAYING"
                                                    2 -> "PAUSED"
                                                    3 -> "BUFFERING"
                                                    5 -> "CUED"
                                                    else -> "STATE_$state"
                                                }
                                                Log.d("TrailerPlayer", "[Method A] State changed to: $playerStateName ($state)")
                                                if (state == 1) {
                                                    isLoading = false
                                                }
                                            }
                                        ),
                                        "AndroidBridge"
                                    )

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            if (newProgress >= 50) isLoading = false
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                Log.d("TrailerPlayer", "[Method A] WebResourceError: ${error?.description}")
                                                lastErrorCode = error?.description?.toString() ?: "network_error"
                                                activeMethod = TrailerPlaybackMethod.METHOD_B
                                            }
                                        }
                                    }

                                    loadDataWithBaseURL(
                                        "https://www.youtube.com/",
                                        embedHtml,
                                        "text/html",
                                        "utf-8",
                                        null
                                    )
                                    webViewInstance = this
                                }
                            },
                            update = { webView ->
                                webViewInstance = webView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                TrailerPlaybackMethod.METHOD_B -> {
                    // STEP 2 B) Direct stream fallback: play in <video> element with same custom controls
                    if (!directStreamUrl.isNullOrBlank()) {
                        val videoHtml = remember(directStreamUrl, isMuted) {
                            val mutedAttr = if (isMuted) "muted" else ""
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                              <style>
                                * { margin:0; padding:0; box-sizing:border-box; background:#000000; overflow:hidden; }
                                html, body { width:100%; height:100%; background:#000000; }
                                video { width:100%; height:100%; object-fit:cover; pointer-events:none; }
                              </style>
                            </head>
                            <body>
                              <video id="directVideo" src="$directStreamUrl" autoplay playsinline $mutedAttr></video>
                              <script>
                                var vid = document.getElementById('directVideo');
                                vid.onplay = function() { if (window.AndroidBridge) window.AndroidBridge.onStateChange(1); };
                                vid.onpause = function() { if (window.AndroidBridge) window.AndroidBridge.onStateChange(2); };
                                vid.ontimeupdate = function() { if (window.AndroidBridge) window.AndroidBridge.onTimeUpdate(vid.currentTime, vid.duration || 0); };
                                vid.onerror = function() { if (window.AndroidBridge) window.AndroidBridge.onPlayerError(999); };
                                function sendCmd(cmd, args) {
                                  if (cmd === 'playVideo') vid.play();
                                  else if (cmd === 'pauseVideo') vid.pause();
                                  else if (cmd === 'seekTo' && args && args[0] !== undefined) vid.currentTime = args[0];
                                  else if (cmd === 'mute') vid.muted = true;
                                  else if (cmd === 'unMute') vid.muted = false;
                                }
                              </script>
                            </body>
                            </html>
                            """.trimIndent()
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false

                                    addJavascriptInterface(
                                        TrailerJsBridge(
                                            onReady = {
                                                isLoading = false
                                                playerStateName = "PLAYING"
                                            },
                                            onError = {
                                                Log.d("TrailerPlayer", "[Method B] Direct video error -> Fallback to Method C")
                                                lastErrorCode = "direct_video_err"
                                                activeMethod = TrailerPlaybackMethod.METHOD_C
                                            },
                                            onTimeUpdate = { curr, dur ->
                                                currentPositionSec = curr
                                                if (dur > 0f) totalDurationSec = dur
                                                if (curr > 0f) isLoading = false
                                            },
                                            onStateChange = { state ->
                                                isPlaying = (state == 1)
                                                playerStateName = if (state == 1) "PLAYING" else "PAUSED"
                                                if (state == 1) isLoading = false
                                            }
                                        ),
                                        "AndroidBridge"
                                    )

                                    loadDataWithBaseURL("https://www.youtube.com/", videoHtml, "text/html", "utf-8", null)
                                    webViewInstance = this
                                }
                            },
                            update = { webView -> webViewInstance = webView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                TrailerPlaybackMethod.METHOD_C -> {
                    // STEP 2 C) Last resort: a small "Watch trailer on YouTube" button opening https://www.youtube.com/watch?v={id}
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                val ytUrl = "https://www.youtube.com/watch?v=$currentTrailerId"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("watch_on_youtube_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Watch trailer on YouTube",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Transparent tap area to toggle play/pause (for Methods A and B)
            if (activeMethod != TrailerPlaybackMethod.METHOD_C) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 44.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { togglePlayPause() }
                            )
                    )

                    // Play/Pause brief animated center indicator
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showPlayPauseIndicator,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    // Plain spinner in the accent color while loading
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isLoading && currentPositionSec == 0f,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Clean in-app controls at the bottom: Custom seek bar + mute/unmute speaker icon
                    TrailerControlBar(
                        currentSec = if (isUserScrubbing) scrubPositionSec else currentPositionSec,
                        totalSec = totalDurationSec,
                        isUserScrubbing = isUserScrubbing,
                        isMuted = isMuted,
                        onScrubStart = { isUserScrubbing = true },
                        onScrubChange = { scrubPositionSec = it },
                        onScrubFinish = {
                            isUserScrubbing = false
                            seekTo(it)
                        },
                        onToggleMute = onToggleMute,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        // DEBUG LINE: toggleable in settings, off by default:
        // title, imdb id, trailer ID source (meta/TMDB/none), TMDB HTTP status, YouTube id, active method (A/B/C), player state, error code
        if (showDebugLine) {
            val methodLetter = when (activeMethod) {
                TrailerPlaybackMethod.METHOD_A -> "A"
                TrailerPlaybackMethod.METHOD_B -> "B"
                TrailerPlaybackMethod.METHOD_C -> "C"
            }
            val tmdbText = tmdbHttpStatus?.toString() ?: "none"
            val debugText = "$movieTitle | ${imdbId.ifBlank { "no_imdb" }} | src:$idSource | tmdb:$tmdbText | yt:${currentTrailerId ?: "none"} | method:$methodLetter | state:$playerStateName | err:$lastErrorCode"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1F22))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("trailer_debug_line")
            ) {
                Text(
                    text = debugText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8AB4F8),
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TrailerControlBar(
    currentSec: Float,
    totalSec: Float,
    isUserScrubbing: Boolean,
    isMuted: Boolean,
    onScrubStart: () -> Unit,
    onScrubChange: (Float) -> Unit,
    onScrubFinish: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun formatTime(seconds: Float): String {
        val total = seconds.toInt().coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                )
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val maxDur = maxOf(1f, totalSec)
            val displayTime = if (totalSec > 0f) "${formatTime(currentSec)} / ${formatTime(totalSec)}" else formatTime(currentSec)

            Text(
                text = displayTime,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Slider(
                value = currentSec.coerceIn(0f, maxDur),
                onValueChange = {
                    onScrubStart()
                    onScrubChange(it)
                },
                onValueChangeFinished = {
                    onScrubFinish(currentSec)
                },
                valueRange = 0f..maxDur,
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .testTag("trailer_seekbar"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                )
            )

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("trailer_volume_toggle")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute Trailer" else "Mute Trailer",
                    tint = if (isMuted) Color.LightGray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
