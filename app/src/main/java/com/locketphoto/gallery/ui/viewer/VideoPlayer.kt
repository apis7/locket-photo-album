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

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun toggleMute() {
        muted = !muted
        player.volume = if (muted) 0f else 1f
    }
}

@OptIn(UnstableApi::class)
@Composable
fun rememberVideoController(item: MediaItem): VideoController {
    val context = LocalContext.current
    val controller = remember(item.id) {
        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(ExoItem.fromUri(item.uri))
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
        }
        controller.player.addListener(listener)
        onDispose {
            controller.player.removeListener(listener)
            controller.player.release()
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller.position = controller.player.currentPosition
            controller.duration = controller.player.duration.coerceAtLeast(0L)
            delay(200)
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
