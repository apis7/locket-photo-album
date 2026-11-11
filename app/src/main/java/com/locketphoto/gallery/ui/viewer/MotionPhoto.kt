package com.locketphoto.gallery.ui.viewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MotionPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * For motion photos: plays the moment once (silently) when you land on it, then settles on the
 * still. A "Motion" pill replays it.
 */
@Composable
fun BoxScope.MotionPhotoLayer(item: MediaItem, active: Boolean, chromeVisible: Boolean) {
    val context = LocalContext.current
    var video by remember(item.id) { mutableStateOf<Uri?>(null) }
    var plays by remember(item.id) { mutableIntStateOf(0) }
    var playing by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, active) {
        if (!active || video != null) return@LaunchedEffect
        val file = withContext(Dispatchers.IO) { MotionPhotos.videoFor(context, item) }
        if (file != null) {
            video = Uri.fromFile(file)
            playing = true
            plays++
        }
    }
    LaunchedEffect(active) { if (!active) playing = false }

    val uri = video ?: return
    if (playing && active) {
        // New controller per play so it always starts from the beginning.
        androidx.compose.runtime.key(plays) {
            val c = rememberVideoController(item, loop = false, uri = uri)
            LaunchedEffect(c) {
                c.player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) playing = false
                    }
                })
            }
            val alpha by animateFloatAsState(if (c.firstFrame) 1f else 0f, tween(180), label = "motionFade")
            VideoSurface(c, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha })
        }
    }

    AnimatedVisibility(
        visible = chromeVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(alpha = if (playing) 0.6f else 0.4f), RoundedCornerShape(50))
                .clickable {
                    plays++
                    playing = true
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(Icons.Rounded.MotionPhotosOn, "Play motion", tint = Color.White, modifier = Modifier.size(16.dp))
            Text(" Motion", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
