@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package org.fossify.gallery.compose.screens.viewer

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Rational
import android.view.LayoutInflater
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.AppMotion
import org.fossify.gallery.compose.theme.BlurRadius
import org.fossify.gallery.compose.util.BlurState
import org.fossify.gallery.compose.util.PipState
import org.fossify.gallery.compose.util.privacyBlur
import org.fossify.gallery.extensions.config
import java.io.File

const val SCALING_FIT = 0
const val SCALING_ZOOM = 2
const val SCALING_FILL_WIDTH = 3

private val mmrReleaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val SPEED_HALF = 0.5f
private const val SPEED_NORMAL = 1f
private const val SPEED_ONE_HALF = 1.5f
private const val SPEED_DOUBLE = 2f
private const val SPEED_TRIPLE = 3f
private const val SCRUB_EXTRACT_DELAY_MS = 400L
private const val NEAR_END_THRESHOLD_MS = 500L
private const val POSITION_POLL_INTERVAL_MS = 500L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val MICROS_PER_MILLI = 1000L
private const val THIRD_DIVISOR = 3f

internal val VIDEO_PLAYBACK_SPEEDS = listOf(
    SPEED_HALF,
    SPEED_NORMAL,
    SPEED_ONE_HALF,
    SPEED_DOUBLE,
    SPEED_TRIPLE,
)

internal fun sanitizeVideoPlaybackSpeed(speed: Float): Float =
    if (speed in VIDEO_PLAYBACK_SPEEDS) speed else SPEED_NORMAL

@SuppressLint("InflateParams")
@Composable
fun VideoPage(
    path: String,
    scalingMode: Int,
    @Suppress("UnusedParameter") onScalingModeChange: (Int) -> Unit,
    onToggleUi: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
    showUi: Boolean = true,
    onInteract: () -> Unit = {},
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    captureFrameRequest: Int = 0,
    onCaptureFrame: (Bitmap?) -> Unit = {},
    sessionMuted: Boolean? = null,
    onMutedChange: ((Boolean) -> Unit)? = null,
    sessionPlaybackSpeed: Float? = null,
    onPlaybackSpeedChange: ((Float) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val zoom = rememberZoomState()
    val latestOnZoomChange by rememberUpdatedState(onZoomChange)
    val latestOnCaptureFrame by rememberUpdatedState(onCaptureFrame)

    var isPlaying by remember(path) { mutableStateOf(ctx.config.autoplayVideos) }
    var localSpeed by remember(path) {
        mutableFloatStateOf(sanitizeVideoPlaybackSpeed(ctx.config.playbackSpeed))
    }
    val playbackSpeed = sessionPlaybackSpeed ?: localSpeed
    var localMuted by remember(path) { mutableStateOf(ctx.config.muteVideos) }
    val isMuted = sessionMuted ?: localMuted
    var trimMode by remember(path) { mutableStateOf(false) }
    var isSavingTrim by remember(path) { mutableStateOf(false) }
    var moreControlsOpen by remember(path) { mutableStateOf(false) }
    var trimStartMs by remember(path) { mutableFloatStateOf(0f) }
    var trimEndMs by remember(path) { mutableFloatStateOf(-1f) }
    var playerError by remember(path) { mutableStateOf<PlaybackException?>(null) }
    var isBuffering by remember(path) { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(-1f) }
    var frameCache by remember(path) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var positionMs by remember(path) { mutableLongStateOf(0L) }
    var pipAspect by remember(path) { mutableStateOf<Rational?>(null) }

    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) zoom.reset() }
    LaunchedEffect(zoom.isZoomed, isCurrentPage) {
        if (isCurrentPage) latestOnZoomChange(zoom.isZoomed)
    }

    val retriever = remember(path) { MediaMetadataRetriever() }
    val frameMutex = remember { Mutex() }
    var retrieverReady by remember(path) { mutableStateOf(false) }

    VideoMetadataAndScrubEffects(
        path = path,
        isCurrentPage = isCurrentPage,
        retriever = retriever,
        frameMutex = frameMutex,
        retrieverReady = retrieverReady,
        hasFrameCache = frameCache.isNotEmpty(),
        onRetrieverReady = { retrieverReady = true },
        onDimensionsReady = { info ->
            zoom.updateContentAspect(info.aspect)
            pipAspect = info.pipAspect
        },
        onFramesReady = { frames -> frameCache = frames },
        onClearAndReleaseFrames = {
            val frames = frameCache
            frameCache = emptyList()
            frames
        },
    )

    val player = rememberVideoExoPlayer(
        path = path,
        isMuted = isMuted,
        playbackSpeed = playbackSpeed,
        isCurrentPage = isCurrentPage,
        onPlayingChange = { isPlaying = it },
        onBufferingChange = { isBuffering = it },
        onError = { playerError = it },
    )

    val playerView = remember {
        (LayoutInflater.from(ctx).inflate(R.layout.compose_video_player_view, null) as PlayerView)
            .apply { useController = false }
    }
    LaunchedEffect(player) { playerView.player = player; positionMs = player.currentPosition }
    LaunchedEffect(scalingMode) { playerView.resizeMode = scalingMode }
    LaunchedEffect(isPlaying, isCurrentPage) { playerView.keepScreenOn = isPlaying && isCurrentPage }

    VideoFrameCaptureAndPipEffects(
        path = path,
        player = player,
        playerView = playerView,
        retriever = retriever,
        frameMutex = frameMutex,
        retrieverReady = retrieverReady,
        captureFrameRequest = captureFrameRequest,
        isCurrentPage = isCurrentPage,
        isPlaying = isPlaying,
        pipAspect = pipAspect,
        onCaptureFrame = latestOnCaptureFrame,
        onPositionTick = { positionMs = it },
    )

    Box(Modifier.fillMaxSize().clipToBounds().background(Color.Black).then(modifier)) {
        VideoPlayerSurface(
            playerView = playerView,
            player = player,
            zoom = zoom,
            allowVideoGestures = ctx.config.allowVideoGestures,
            onToggleUi = onToggleUi,
            onSeekPosition = { positionMs = it; onInteract() },
        )

        ZoomMinimap(
            zoom,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 72.dp, end = 12.dp),
        )

        VideoPlaybackStatusOverlay(
            path = path,
            isBuffering = isBuffering,
            playerError = playerError,
            onRetry = {
                playerError = null
                player.prepare()
                player.playWhenReady = ctx.config.autoplayVideos
            },
        )

        AnimatedVisibility(
            visible = showUi && playerError == null,
            enter = fadeIn(AppMotion.medium),
            exit = fadeOut(AppMotion.medium),
        ) {
            VideoPageOverlayControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                playbackSpeed = playbackSpeed,
                moreControlsOpen = moreControlsOpen,
                trimMode = trimMode,
                isSavingTrim = isSavingTrim,
                durationMs = player.duration,
                positionMs = positionMs,
                scrubFraction = scrubFraction,
                frameCache = frameCache,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                onTogglePlayPause = {
                    togglePlayerPlayback(player, isPlaying)
                    onInteract()
                },
                onToggleMute = {
                    val updatedMuted = !isMuted
                    localMuted = updatedMuted
                    onMutedChange?.invoke(updatedMuted)
                    player.volume = if (updatedMuted) 0f else 1f
                    onInteract()
                },
                onOpenMoreControls = { moreControlsOpen = true; onInteract() },
                onDismissMoreControls = { moreControlsOpen = false },
                onCycleSpeed = { nextSpeed ->
                    localSpeed = nextSpeed
                    onPlaybackSpeedChange?.invoke(nextSpeed)
                    ctx.config.playbackSpeed = nextSpeed
                    player.setPlaybackSpeed(nextSpeed)
                    moreControlsOpen = false
                    onInteract()
                },
                onEnterPip = {
                    enterVideoPip(ctx as? Activity, pipAspect, player, isPlaying)
                    moreControlsOpen = false
                    onInteract()
                },
                onToggleTrimMode = {
                    trimMode = !trimMode
                    if (trimMode && trimEndMs < 0f) trimEndMs = player.duration.toFloat()
                    moreControlsOpen = false
                    onInteract()
                },
                onSeekDrag = { fraction ->
                    scrubFraction = fraction
                    player.seekTo((fraction * player.duration).toLong())
                    onInteract()
                },
                onSeekFinish = { finalPos ->
                    positionMs = finalPos
                    scrubFraction = -1f
                },
                onUpdateTrimRange = { start, end, seekMs ->
                    trimStartMs = start
                    trimEndMs = end
                    player.seekTo(seekMs)
                    positionMs = seekMs
                    onInteract()
                },
                onCancelTrim = { trimMode = false; onInteract() },
                onSaveTrim = { startMs, endMs ->
                    onInteract()
                    if (endMs <= startMs) {
                        ctx.toast(R.string.trim_invalid_range, Toast.LENGTH_SHORT)
                    } else {
                        isSavingTrim = true
                        scope.launch {
                            val result = exportTrimmedMp4(
                                ctx = ctx,
                                path = path,
                                startUs = startMs.toLong() * MICROS_PER_MILLI,
                                endUs = endMs.toLong() * MICROS_PER_MILLI,
                            )
                            isSavingTrim = false
                            result.onSuccess { file ->
                                trimMode = false
                                ctx.toast(res.getString(R.string.saved_as, file.name), Toast.LENGTH_SHORT)
                            }.onFailure { err ->
                                val msg = err.message ?: err.javaClass.simpleName
                                ctx.toast(res.getString(R.string.error_generic, msg), Toast.LENGTH_SHORT)
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun VideoMetadataAndScrubEffects(
    path: String,
    isCurrentPage: Boolean,
    retriever: MediaMetadataRetriever,
    frameMutex: Mutex,
    retrieverReady: Boolean,
    hasFrameCache: Boolean,
    onRetrieverReady: () -> Unit,
    onDimensionsReady: (VideoDimensionsInfo) -> Unit,
    onFramesReady: (List<Bitmap>) -> Unit,
    onClearAndReleaseFrames: () -> List<Bitmap>,
) {
    val latestOnRetrieverReady by rememberUpdatedState(onRetrieverReady)
    val latestOnDimensionsReady by rememberUpdatedState(onDimensionsReady)
    val latestOnFramesReady by rememberUpdatedState(onFramesReady)
    val latestClearFrames by rememberUpdatedState(onClearAndReleaseFrames)

    LaunchedEffect(path) {
        val info = withContext(Dispatchers.IO) {
            frameMutex.withLock {
                runCatching { readVideoDimensionsInfo(retriever, path) }.getOrNull()
            }
        }
        latestOnRetrieverReady()
        info?.let { latestOnDimensionsReady(it) }
    }
    LaunchedEffect(path, isCurrentPage, retrieverReady) {
        if (!isCurrentPage || !retrieverReady || hasFrameCache) return@LaunchedEffect
        delay(SCRUB_EXTRACT_DELAY_MS)
        val frames = extractVideoScrubFrames(retriever, frameMutex)
        try {
            coroutineContext.ensureActive()
            latestOnFramesReady(frames)
        } catch (e: CancellationException) {
            releaseScrubFrames(frames)
            throw e
        }
    }
    DisposableEffect(path) {
        onDispose {
            val frames = latestClearFrames()
            mmrReleaseScope.launch {
                frameMutex.withLock {
                    releaseScrubFrames(frames)
                    runCatching { retriever.release() }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerSurface(
    playerView: PlayerView,
    player: ExoPlayer,
    zoom: ZoomState,
    allowVideoGestures: Boolean,
    onToggleUi: () -> Unit,
    onSeekPosition: (Long) -> Unit,
) {
    AndroidView(
        factory = { playerView },
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }
            .zoomable(
                zoom,
                onSingleTap = onToggleUi,
                onDoubleTap = if (allowVideoGestures) {
                    { pos, sz ->
                        handleVideoDoubleTap(
                            x = pos.x,
                            width = sz.width.toFloat(),
                            player = player,
                            onSeek = onSeekPosition,
                            onZoom = { zoom.cycleZoom(pos, sz) },
                        )
                    }
                } else {
                    null
                },
            )
            .privacyBlur(BlurRadius.viewer, BlurState.enabled),
    )
}

@Composable
private fun VideoPlaybackStatusOverlay(
    path: String,
    isBuffering: Boolean,
    playerError: PlaybackException?,
    onRetry: () -> Unit,
) {
    if (isBuffering && playerError == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }

    if (playerError != null) {
        val file = remember(path) { File(path) }
        ViewerMediaErrorState(
            file = file,
            isAvailable = file.exists(),
            onRetry = onRetry,
        )
    }
}

private fun buildVideoExoPlayer(
    ctx: android.content.Context,
    path: String,
    isMuted: Boolean,
    playbackSpeed: Float,
): ExoPlayer = ExoPlayer.Builder(ctx)
    .setHandleAudioBecomingNoisy(true)
    .build()
    .apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        repeatMode = if (ctx.config.loopVideos) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        volume = if (isMuted) 0f else 1f
        setPlaybackSpeed(playbackSpeed)
        if (ctx.config.rememberLastVideoPosition) {
            val savedPos = ctx.config.getLastVideoPosition(path)
            if (savedPos > 0) seekTo(savedPos.toLong())
        }
    }

@Composable
private fun rememberVideoExoPlayer(
    path: String,
    isMuted: Boolean,
    playbackSpeed: Float,
    isCurrentPage: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onBufferingChange: (Boolean) -> Unit,
    onError: (PlaybackException) -> Unit,
): ExoPlayer {
    val ctx = LocalContext.current
    val latestOnPlaying by rememberUpdatedState(onPlayingChange)
    val latestOnBuffering by rememberUpdatedState(onBufferingChange)
    val latestOnError by rememberUpdatedState(onError)

    val player = remember(path) { buildVideoExoPlayer(ctx, path, isMuted, playbackSpeed) }
    LaunchedEffect(player, isMuted) { player.volume = if (isMuted) 0f else 1f }
    LaunchedEffect(player, playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(player, isCurrentPage) {
        if (isCurrentPage) {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            } else if (player.playbackState == Player.STATE_ENDED && ctx.config.autoplayVideos) {
                player.seekTo(0)
            }
            player.playWhenReady = ctx.config.autoplayVideos
        } else {
            player.playWhenReady = false
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) = latestOnPlaying(playing)
            override fun onPlaybackStateChanged(state: Int) =
                latestOnBuffering(state == Player.STATE_BUFFERING)
            override fun onPlayerError(error: PlaybackException) = latestOnError(error)
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.playWhenReady = false
            player.pause()
            val nearEnd = player.duration > 0L &&
                player.currentPosition >= player.duration - NEAR_END_THRESHOLD_MS
            if (ctx.config.rememberLastVideoPosition && !nearEnd) {
                ctx.config.saveLastVideoPosition(path, player.currentPosition.toInt())
            }
            player.release()
        }
    }
    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner, player) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }
    return player
}

@Composable
private fun VideoFrameCaptureAndPipEffects(
    path: String,
    player: ExoPlayer,
    playerView: PlayerView,
    retriever: MediaMetadataRetriever,
    frameMutex: Mutex,
    retrieverReady: Boolean,
    captureFrameRequest: Int,
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    pipAspect: Rational?,
    onCaptureFrame: (Bitmap?) -> Unit,
    onPositionTick: (Long) -> Unit,
) {
    val ctx = LocalContext.current
    val latestOnCaptureFrame by rememberUpdatedState(onCaptureFrame)
    val latestOnPositionTick by rememberUpdatedState(onPositionTick)

    LaunchedEffect(captureFrameRequest, isCurrentPage, player) {
        if (captureFrameRequest <= 0 || !isCurrentPage) return@LaunchedEffect
        val currentPositionUs = player.currentPosition.coerceAtLeast(0L) * MICROS_PER_MILLI
        val frame = (playerView.videoSurfaceView as? TextureView)?.bitmap
            ?: withContext(Dispatchers.IO) {
                frameMutex.withLock {
                    runCatching {
                        if (!retrieverReady) retriever.setDataSource(path)
                        retriever.getFrameAtTime(
                            currentPositionUs,
                            MediaMetadataRetriever.OPTION_CLOSEST,
                        )
                    }.getOrNull()
                }
            }
        latestOnCaptureFrame(frame)
    }
    LaunchedEffect(isPlaying, isCurrentPage, pipAspect) {
        val active = if (isPlaying && isCurrentPage) pipAspect else null
        PipState.activeVideoAspect = active
        updateAutoPipParams(ctx as? Activity, active)
    }
    DisposableEffect(path) {
        onDispose {
            PipState.activeVideoAspect = null
            updateAutoPipParams(ctx as? Activity, null)
        }
    }
    LaunchedEffect(isCurrentPage, isPlaying) {
        while (isCurrentPage && isPlaying) {
            latestOnPositionTick(player.currentPosition)
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }
}

private fun togglePlayerPlayback(player: ExoPlayer, isPlaying: Boolean) {
    if (isPlaying) {
        player.pause()
    } else {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }
}

private fun handleVideoDoubleTap(
    x: Float,
    width: Float,
    player: ExoPlayer,
    onSeek: (Long) -> Unit,
    onZoom: () -> Unit,
) {
    when {
        x < width / THIRD_DIVISOR -> {
            val target = (player.currentPosition - DOUBLE_TAP_SEEK_MS).coerceAtLeast(0L)
            player.seekTo(target)
            onSeek(target)
        }
        x > width * 2f / THIRD_DIVISOR -> {
            val dur = player.duration
            if (dur > 0L) {
                val target = (player.currentPosition + DOUBLE_TAP_SEEK_MS).coerceAtMost(dur)
                player.seekTo(target)
                onSeek(target)
            }
        }
        else -> onZoom()
    }
}
