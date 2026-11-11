package com.locketphoto.gallery.ui.viewer

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.mp4.SlowMotionData
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as ExoItem

/** Holds one ExoPlayer for the page on screen. Starts silent and loops, like a live photo. */
class VideoController(val player: ExoPlayer) {
    var playing by mutableStateOf(true)
    var muted by mutableStateOf(true)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var firstFrame by mutableStateOf(false)

    /** Slow-motion sections (ms ranges with a speed), found in the file's metadata. */
    var slowMo by mutableStateOf<List<SlowSegment>>(emptyList())
    /** Lets the user watch a slow-mo clip at real speed. */
    var slowMoOn by mutableStateOf(true)

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun toggleMute() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
    }

    internal fun speedAt(ms: Long): Float =
        if (!slowMoOn) 1f else slowMo.firstOrNull { ms in it.startMs until it.endMs }?.speed ?: 1f
}

data class SlowSegment(val startMs: Long, val endMs: Long, val speed: Float)

/**
 * Reads slow-motion info from track metadata:
 * - Samsung SEF slow-mo: exact segments and speed divisors.
 * - Standard high-frame-rate capture ("com.android.capture.fps" >= 90): slow the middle,
 *   like the phone's own gallery does.
 */
@OptIn(UnstableApi::class)
private fun slowSegmentsOf(tracks: Tracks, durationMs: Long): List<SlowSegment> {
    var captureFps = 0f
    for (group in tracks.groups) for (i in 0 until group.length) {
        val format = group.getTrackFormat(i)
        val md = format.metadata ?: continue
        for (k in 0 until md.length()) {
            when (val e = md.get(k)) {
                is SlowMotionData -> return e.segments.map { seg ->
                    SlowSegment(seg.startTimeMs, seg.endTimeMs, 1f / seg.speedDivisor.coerceAtLeast(1))
                }
                is MdtaMetadataEntry -> if (e.key == "com.android.capture.fps" && e.value.size >= 4) {
                    captureFps = java.nio.ByteBuffer.wrap(e.value).float
                }
            }
        }
    }
    if (captureFps >= 90f && durationMs > 1500) {
        val speed = (30f / captureFps).coerceIn(0.125f, 1f)
        return listOf(SlowSegment((durationMs * 0.15).toLong(), (durationMs * 0.85).toLong(), speed))
    }
    return emptyList()
}

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoController(item: MediaItem, loop: Boolean = true, uri: android.net.Uri = item.uri): VideoController {
    val context = LocalContext.current
    val controller = remember(item.id, uri) {
        // FLAG_READ_SEF_DATA exposes Samsung slow-motion segments as track metadata.
        val extractors = DefaultExtractorsFactory().setMp4ExtractorFlags(Mp4Extractor.FLAG_READ_SEF_DATA)
        val player = ExoPlayer.Builder(context, DefaultMediaSourceFactory(context, extractors)).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = 0f
            setMediaItem(ExoItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        VideoController(player)
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                controller.playing = isPlaying
            }

            override fun onRenderedFirstFrame() {
                controller.firstFrame = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                controller.slowMo = slowSegmentsOf(tracks, controller.player.duration.coerceAtLeast(item.durationMs))
            }
        }
        controller.player.addListener(listener)
        onDispose {
            controller.player.removeListener(listener)
            controller.player.release()
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            val pos = controller.player.currentPosition
            controller.position = pos
            controller.duration = controller.player.duration.coerceAtLeast(0L)
            // Apply slow-motion speed for whatever section we're in.
            val want = controller.speedAt(pos)
            if (controller.player.playbackParameters.speed != want) controller.player.setPlaybackSpeed(want)
            delay(if (controller.slowMo.isEmpty()) 200 else 40)
        }
    }
    return controller
}

/** The video picture itself. Shown over the thumbnail once the first frame is ready. */
@Composable
fun VideoSurface(controller: VideoController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> TextureView(ctx).also { controller.player.setVideoTextureView(it) } },
        onRelease = { controller.player.clearVideoTextureView(it) },
        modifier = modifier,
    )
}

/** Play/pause, scrubber, time and mute — slides in with the rest of the viewer chrome. */
@Composable
fun VideoControls(controller: VideoController, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        var scrubbing by remember { mutableStateOf(false) }
        var scrubValue by remember { mutableFloatStateOf(0f) }
        val dur = controller.duration.coerceAtLeast(1L)
        val shown = if (scrubbing) scrubValue else controller.position.toFloat() / dur

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = controller::togglePlay) {
                Icon(if (controller.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", tint = Color.White)
            }
            Text(formatTime(if (scrubbing) (scrubValue * dur).toLong() else controller.position), color = Color.White, fontSize = 12.sp)
            Slider(
                value = shown.coerceIn(0f, 1f),
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                    controller.player.seekTo((it * dur).toLong())
                },
                onValueChangeFinished = { scrubbing = false },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(controller.duration), color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            if (controller.slowMo.isNotEmpty()) {
                IconButton(onClick = { controller.slowMoOn = !controller.slowMoOn }) {
                    Icon(
                        Icons.Rounded.SlowMotionVideo,
                        if (controller.slowMoOn) "Play at normal speed" else "Play in slow motion",
                        tint = if (controller.slowMoOn) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
            IconButton(onClick = controller::toggleMute) {
                Icon(
                    if (controller.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    if (controller.muted) "Unmute" else "Mute",
                    tint = Color.White,
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
