package com.review.mediamanager.ui.viewer

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import com.review.mediamanager.LocalPip
import com.review.mediamanager.database.MediaEntity
import com.review.mediamanager.player.PlayerFactory
import com.review.mediamanager.repository.MediaRepository
import com.review.mediamanager.ui.findActivity
import com.review.mediamanager.ui.theme.Aurora
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Live information reported by the decoder while playing (shown in the info panel). */
class PlaybackStats {
    val decoder = mutableStateOf<String?>(null)
    val streamFps = mutableStateOf<Float?>(null)
    val streamSize = mutableStateOf<String?>(null)
    val dropped = mutableStateOf(0)
}

class PlayerHandle {
    var player: ExoPlayer? = null
    val speed = mutableStateOf(1f)
    val landscape = mutableStateOf(false)
    val stats = PlaybackStats()
    fun setSpeed(s: Float) { speed.value = s; player?.setPlaybackSpeed(s) }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoPlayer(
    item: MediaEntity,
    repo: MediaRepository,
    handle: PlayerHandle,
    uiVisible: MutableState<Boolean>
) {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val pip = LocalPip.current
    val inPip by (pip?.inPip ?: remember { mutableStateOf(false) })
    val lifecycleOwner = LocalLifecycleOwner.current
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var indicator by remember { mutableStateOf<Pair<Boolean, Float>?>(null) } // (isBrightness, level)
    var indicatorTick by remember { mutableStateOf(0) }

    val player = remember(item.id) {
        PlayerFactory.create(ctx, repo.storage.mediaFile(item), item.inVault, repo.keys).also { it.playWhenReady = true }
    }

    DisposableEffect(player) {
        handle.player = player
        player.setPlaybackSpeed(handle.speed.value)
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
                    pip?.aspect = Rational(w, videoSize.height)
                    pip?.refresh()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { pip?.refresh() }
            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                        "This phone's video decoder can't play this resolution/frame rate. (${e.errorCodeName})"
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "This video format or codec isn't supported on this phone. (${e.errorCodeName})"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                        "The video file seems damaged or incomplete. (${e.errorCodeName})"
                    else -> "Playback failed: ${e.errorCodeName}"
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long
            ) {
                val software = decoderName.startsWith("OMX.google.") || decoderName.startsWith("c2.android.")
                handle.stats.decoder.value = decoderName + if (software) " (software)" else " (hardware)"
            }
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                handle.stats.dropped.value += droppedFrames
            }
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                handle.stats.streamSize.value = "${format.width}×${format.height}"
                handle.stats.streamFps.value = format.frameRate.takeIf { it > 0f }
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        pip?.playerActive = true
        pip?.isPlaying = { player.isPlaying }
        pip?.refresh()

        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            player.release()
            handle.player = null
            pip?.playerActive = false
            pip?.isPlaying = { false }
            pip?.refresh()
            activity?.window?.let { w ->
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(handle.landscape.value) {
        activity?.requestedOrientation = if (handle.landscape.value) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    LaunchedEffect(indicatorTick) {
        if (indicator != null) { delay(900); indicator = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerShowTimeoutMs = 3500
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        uiVisible.value = v == View.VISIBLE
                    })

                    // Gestures: left half = brightness, right half = volume, double tap edges = ±10 s.
                    val audio = c.getSystemService(AudioManager::class.java)
                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    var vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    var mode = 0
                    val slop = android.view.ViewConfiguration.get(c).scaledTouchSlop
                    val view = this
                    val detector = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onDown(e: MotionEvent): Boolean = true
                        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                            if (e1 == null) return false
                            if (mode == 0) {
                                val dy = abs(e2.y - e1.y); val dx = abs(e2.x - e1.x)
                                if (dy > slop * 2 && dy > dx * 1.5f) {
                                    mode = if (e1.x < view.width / 2f) 1 else 2
                                    vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                } else return false
                            }
                            val delta = distanceY / view.height.coerceAtLeast(1) * 1.4f
                            if (mode == 1) {
                                val w = activity?.window ?: return true
                                val cur = w.attributes.screenBrightness.let { if (it < 0) 0.5f else it }
                                val nb = (cur + delta).coerceIn(0.01f, 1f)
                                w.attributes = w.attributes.apply { screenBrightness = nb }
                                indicator = true to nb
                            } else {
                                vol = (vol + delta * maxVol).coerceIn(0f, maxVol.toFloat())
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0)
                                indicator = false to vol / maxVol
                            }
                            indicatorTick++
                            return true
                        }
                        override fun onDoubleTap(e: MotionEvent): Boolean {
                            when {
                                e.x < view.width / 3f -> player.seekBack()
                                e.x > view.width * 2 / 3f -> player.seekForward()
                                else -> if (player.isPlaying) player.pause() else player.play()
                            }
                            return true
                        }
                    })
                    setOnTouchListener { _, ev ->
                        detector.onTouchEvent(ev)
                        val consumed = mode != 0
                        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) mode = 0
                        consumed
                    }
                }
            },
            update = { v -> v.useController = !inPip }
        )

        indicator?.let { (bright, level) ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).background(Color(0xCC000000)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(if (bright) Icons.Rounded.Brightness6 else Icons.Rounded.VolumeUp, null, tint = Aurora.Mint)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { level }, modifier = Modifier.width(120.dp).clip(CircleShape),
                    color = Aurora.Mint, trackColor = Color(0x33FFFFFF))
                Spacer(Modifier.height(6.dp))
                Text("${(level * 100).roundToInt()}%", color = Aurora.TextHi, style = MaterialTheme.typography.labelMedium)
            }
        }

        error?.let {
            Column(Modifier.align(Alignment.Center).padding(30.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE6000000)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Aurora.Coral)
                Spacer(Modifier.height(8.dp))
                Text(it, color = Aurora.TextHi, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
