package com.locketphoto.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.edit.Adjust
import com.locketphoto.gallery.edit.AspectChoice
import com.locketphoto.gallery.edit.ColorMath
import com.locketphoto.gallery.edit.CropRect
import com.locketphoto.gallery.edit.Filters
import com.locketphoto.gallery.edit.Geometry
import com.locketphoto.gallery.edit.MediaSaver
import com.locketphoto.gallery.edit.NPoint
import com.locketphoto.gallery.edit.VideoEdit
import com.locketphoto.gallery.edit.VideoExport
import com.locketphoto.gallery.edit.VideoInfo
import com.locketphoto.gallery.ui.theme.Gold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as ExoItem

@OptIn(UnstableApi::class)
@Composable
internal fun VideoEditor(
    item: MediaItem,
    requestWrite: (Uri, () -> Unit) -> Unit,
    onDone: (Uri?) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<VideoInfo?>(null) }
    var frames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(item.uri) {
        val i = withContext(Dispatchers.IO) { runCatching { VideoExport.probe(context, item.uri) }.getOrNull() }
            ?: VideoInfo(item.displayWidth.coerceAtLeast(1), item.displayHeight.coerceAtLeast(1), item.durationMs, false)
        info = i
        frames = withContext(Dispatchers.IO) { VideoExport.filmstrip(context, item.uri, i.durationMs, 10, 120) }
    }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(ExoItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    val history = remember(item.uri) { EditHistory(VideoEdit()) }
    val edit = history.current
    val editNow by rememberUpdatedState(edit)
    var tab by remember { mutableStateOf("trim") }
    var askDiscard by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val overwriteOk = MediaSaver.canOverwriteVideo(item.mimeType)

    // Loop inside the trimmed range; reflect mute.
    LaunchedEffect(player, info) {
        while (true) {
            val i = info
            val e = editNow
            position = player.currentPosition
            if (i != null && !i.isSefSlowMo) {
                val end = if (e.trimEndMs > 0) e.trimEndMs else i.durationMs
                if (player.isPlaying && (position >= end - 30 || position < e.trimStartMs - 200)) player.seekTo(e.trimStartMs)
            }
            delay(40)
        }
    }
    LaunchedEffect(edit.muted) { player.volume = if (edit.muted) 0f else 1f }

    BackHandler(enabled = exportJob == null) { if (history.dirty) askDiscard = true else onCancel() }

    fun runSave(copy: Boolean) {
        val i = info ?: return
        val snapshot = history.current
        val go = {
            player.pause()
            progress = 0f
            exportJob = scope.launch {
                val out = File.createTempFile("locket_video_", ".mp4", context.cacheDir)
                try {
                    VideoExport.export(context, item.uri, snapshot, i, out) { progress = it * 0.95f }
                    progress = 0.97f
                    val uri = withContext(Dispatchers.IO) {
                        if (copy) MediaSaver.saveVideoCopy(context, item, out)
                        else { MediaSaver.overwriteVideo(context, item, out); item.uri }
                    }
                    exportJob = null
                    onDone(uri)
                } catch (e: CancellationException) {
                    exportJob = null
                    throw e
                } catch (e: Throwable) {
                    exportJob = null
                    Toast.makeText(context, "Couldn't save video: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                } finally {
                    out.delete()
                }
            }
            Unit
        }
        if (copy) go() else requestWrite(item.uri) { go() }
    }

    Column(Modifier.fillMaxSize().background(EditorBg)) {
        EditorTopBar(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            canSave = history.dirty && exportJob == null && info != null,
            onCancel = { if (history.dirty) askDiscard = true else onCancel() },
            onUndo = history::undo,
            onRedo = history::redo,
            onSave = { runSave(false) },
            onSaveCopy = { runSave(true) },
            overwriteAllowed = overwriteOk,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            info?.let { i ->
                VideoViewport(
                    player = player,
                    info = i,
                    edit = edit,
                    tab = tab,
                    playing = playing,
                    onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                    onUpdate = history::update,
                    onCommit = history::commit,
                )
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                    scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.96f)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessHigh))
            },
            label = "vpanel",
            modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp),
        ) { t ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                val i = info
                when {
                    i == null -> Text("Loading…", color = Muted)
                    t == "trim" -> TrimPanel(i, edit, frames, position, onChange = { s, e -> history.update(edit.copy(trimStartMs = s, trimEndMs = e)) }, onSeek = { player.seekTo(it) }, onCommit = history::commit)
                    t == "crop" -> VideoCropPanel(edit, i, history::set)
                    t == "filters" -> {
                        val thumb = remember(frames) {
                            frames.getOrNull(frames.size / 2)?.let { b ->
                                val side = min(b.width, b.height)
                                Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side).asImageBitmap()
                            }
                        }
                        Column {
                            LocketSlider(
                                label = "Intensity",
                                value = edit.filterIntensity,
                                range = 0f..1f,
                                resetTo = 1f,
                                enabled = edit.filterId != Filters.none.id,
                                format = { "${(it * 100).roundToInt()}" },
                                onChange = { history.update(edit.copy(filterIntensity = it)) },
                                onFinished = history::commit,
                            )
                            Spacer(Modifier.height(6.dp))
                            FilterStrip(thumb, edit.filterId) { history.set(edit.copy(filterId = it, filterIntensity = 1f)) }
                        }
                    }
                    t == "text" -> TextPanel(edit, history::update, history::commit, history::set)
                    else -> AudioPanel(edit.muted) { history.set(edit.copy(muted = !edit.muted)) }
                }
            }
        }
        ToolTabs(
            tabs = listOf(
                ToolTab("trim", "Trim", Icons.Rounded.ContentCut),
                ToolTab("crop", "Crop", Icons.Rounded.Crop),
                ToolTab("filters", "Filters", Icons.Rounded.Filter),
                ToolTab("text", "Text", Icons.Rounded.TextFields),
                ToolTab("audio", "Audio", Icons.Rounded.GraphicEq),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(4.dp))
    }
    if (askDiscard) DiscardDialog(onDiscard = { askDiscard = false; onCancel() }, onKeep = { askDiscard = false })
    if (exportJob != null) SavingOverlay("Exporting video", progress) { exportJob?.cancel(); exportJob = null }
}

@Composable
private fun VideoViewport(
    player: ExoPlayer,
    info: VideoInfo,
    edit: VideoEdit,
    tab: String,
    playing: Boolean,
    onTogglePlay: () -> Unit,
    onUpdate: (VideoEdit) -> Unit,
    onCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val editNow by rememberUpdatedState(edit)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vw = with(density) { maxWidth.toPx() }
        val vh = with(density) { maxHeight.toPx() }
        val cropMode = tab == "crop"
        val pad = with(density) { (if (cropMode) 28.dp else 12.dp).toPx() }
        val g = edit.geometry
        val sw = info.width.toFloat()
        val sh = info.height.toFloat()
        val fw = g.frameW(sw, sh)
        val fh = g.frameH(sw, sh)
        val rx = if (cropMode) 0f else g.crop.left * fw
        val ry = if (cropMode) 0f else g.crop.top * fh
        val rw = if (cropMode) fw else g.crop.width * fw
        val rh = if (cropMode) fh else g.crop.height * fh
        val fitT = min((vw - 2 * pad) / rw, (vh - 2 * pad) / rh).coerceAtLeast(0.0001f)
        val sp = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        val fit by animateFloatAsState(fitT, sp, label = "vfit")
        val offX by animateFloatAsState((vw - rw * fitT) / 2f - rx * fitT, sp, label = "voffX")
        val offY by animateFloatAsState((vh - rh * fitT) / 2f - ry * fitT, sp, label = "voffY")
        val clipL = offX + rx * fit
        val clipT = offY + ry * fit
        val clipW = rw * fit
        val clipH = rh * fit

        val matrix = remember(edit.filterId, edit.filterIntensity) { ColorMath.matrix(Adjust(), edit.filterId, edit.filterIntensity) }

        // Picture: clip to the crop window; the TextureView is placed and turned inside it.
        Box(
            Modifier
                .layout { m, c ->
                    val p = m.measure(Constraints.fixed(clipW.roundToInt().coerceAtLeast(1), clipH.roundToInt().coerceAtLeast(1)))
                    layout(c.maxWidth, c.maxHeight) { p.place(clipL.roundToInt(), clipT.roundToInt()) }
                }
                .clipToBounds(),
        ) {
            val cx = offX + fw * fit / 2f - clipL
            val cy = offY + fh * fit / 2f - clipT
            val w = sw * fit
            val h = sh * fit
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                update = { tv ->
                    tv.setLayerPaint(if (ColorMath.isIdentity(matrix)) null else Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                },
                onRelease = { player.clearVideoTextureView(it) },
                modifier = Modifier
                    .layout { m, _ ->
                        val p = m.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)))
                        layout(0, 0) { p.place((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()) }
                    }
                    .graphicsLayer {
                        // Mirror after turning, in view space: M * R(t) == R(-t) * M.
                        rotationZ = if (g.mirror) -90f * g.quarterTurns else 90f * g.quarterTurns
                        scaleX = if (g.mirror) -1f else 1f
                    },
            )
        }

        if (cropMode) {
            CropOverlay(
                frame = frameRect(offX, offY, fw * fit, fh * fit),
                crop = g.crop,
                ratio = g.aspect.ratio(fw / fh),
                onChange = { onUpdate(editNow.copy(geometry = editNow.geometry.copy(crop = it))) },
                onEnd = onCommit,
            )
        } else {
            // Text overlay preview, same proportions as the export (height-relative).
            if (edit.text.text.isNotBlank()) {
                val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER } }
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas { c ->
                        val t = edit.text
                        paint.textSize = max(8f, t.size * clipH)
                        paint.color = t.color
                        val lum = (0.2126f * android.graphics.Color.red(t.color) + 0.7152f * android.graphics.Color.green(t.color) + 0.0722f * android.graphics.Color.blue(t.color)) / 255f
                        paint.setShadowLayer(paint.textSize * 0.06f, 0f, paint.textSize * 0.03f, if (lum > 0.5f) android.graphics.Color.argb(140, 0, 0, 0) else android.graphics.Color.argb(140, 255, 255, 255))
                        val fm = paint.fontMetrics
                        c.nativeCanvas.save()
                        c.nativeCanvas.clipRect(clipL, clipT, clipL + clipW, clipT + clipH)
                        c.nativeCanvas.drawText(t.text, clipL + t.at.x * clipW, clipT + t.at.y * clipH - (fm.ascent + fm.descent) / 2f, paint)
                        c.nativeCanvas.restore()
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onTogglePlay() } }
                    .pointerInput(tab, clipL, clipT, clipW, clipH) {
                        if (tab != "text") return@pointerInput
                        detectDragGestures(onDragEnd = onCommit, onDragCancel = onCommit) { change, d ->
                            change.consume()
                            val t = editNow.text
                            onUpdate(editNow.copy(text = t.copy(at = NPoint((t.at.x + d.x / clipW).coerceIn(0f, 1f), (t.at.y + d.y / clipH).coerceIn(0f, 1f)))))
                        }
                    },
            )
            AnimatedVisibility(
                visible = !playing,
                enter = fadeIn() + scaleIn(spring(dampingRatio = 0.5f)),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, "Play", tint = Color.White,
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun TrimPanel(
    info: VideoInfo,
    edit: VideoEdit,
    frames: List<Bitmap>,
    position: Long,
    onChange: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onCommit: () -> Unit,
) {
    val dur = info.durationMs.coerceAtLeast(1L)
    val start = edit.trimStartMs
    val end = if (edit.trimEndMs > 0) edit.trimEndMs else dur
    val startNow by rememberUpdatedState(start)
    val endNow by rememberUpdatedState(end)
    val images = remember(frames) { frames.map { it.asImageBitmap() } }
    val enabled = !info.isSefSlowMo
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(start), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${fmt(end - start)} selected", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(fmt(end), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
            val wPx = with(LocalDensity.current) { maxWidth.toPx() }
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Panel)) {
                for (img in images) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
            }
            val minGap = (dur * 0.03f).toLong().coerceAtLeast(300L).coerceAtMost(dur)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, dur) {
                        if (!enabled) return@pointerInput
                        var which = 0
                        detectDragGestures(
                            onDragStart = { p ->
                                val sx = startNow.toFloat() / dur * wPx
                                val ex = endNow.toFloat() / dur * wPx
                                which = if (abs(p.x - sx) <= abs(p.x - ex)) 0 else 1
                            },
                            onDragEnd = onCommit,
                            onDragCancel = onCommit,
                        ) { change, _ ->
                            change.consume()
                            val t = (change.position.x / wPx * dur).toLong().coerceIn(0L, dur)
                            if (which == 0) {
                                val s = t.coerceAtMost(endNow - minGap).coerceAtLeast(0L)
                                onChange(s, if (endNow >= dur) 0L else endNow); onSeek(s)
                            } else {
                                val e = t.coerceAtLeast(startNow + minGap).coerceAtMost(dur)
                                onChange(startNow, if (e >= dur) 0L else e); onSeek(max(startNow, e - 1000))
                            }
                        }
                    },
            ) {
                val sx = start.toFloat() / dur * size.width
                val ex = end.toFloat() / dur * size.width
                drawRect(Color.Black.copy(alpha = 0.6f), Offset.Zero, Size(sx, size.height))
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(ex, 0f), Size(size.width - ex, size.height))
                val bw = 3.dp.toPx()
                drawRoundRect(Gold, Offset(sx, 0f), Size(ex - sx, size.height), CornerRadius(8.dp.toPx()), style = DrawStroke(bw))
                val hw = 10.dp.toPx()
                drawRoundRect(Gold, Offset(sx, 0f), Size(hw, size.height), CornerRadius(6.dp.toPx()))
                drawRoundRect(Gold, Offset(ex - hw, 0f), Size(hw, size.height), CornerRadius(6.dp.toPx()))
                val px = position.toFloat() / dur * size.width
                drawLine(Color.White, Offset(px, -4.dp.toPx()), Offset(px, size.height + 4.dp.toPx()), 2.dp.toPx())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (enabled) "Drag the gold handles to trim" else "Slow-motion clip: trimming is off so the slow motion is kept on save",
            color = Muted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun VideoCropPanel(edit: VideoEdit, info: VideoInfo, onSet: (VideoEdit) -> Unit) {
    val g = edit.geometry
    val frameRatio = g.frameW(info.width.toFloat(), info.height.toFloat()) / g.frameH(info.width.toFloat(), info.height.toFloat())
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (a in AspectChoice.entries) {
                Chip(a.label, g.aspect == a, onClick = {
                    val ratio = a.ratio(frameRatio)
                    onSet(edit.copy(geometry = g.copy(aspect = a, crop = if (ratio == null) g.crop else CropRect.centered(ratio, frameRatio, 1f))))
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RoundToolButton(Icons.Rounded.RotateRight, "Rotate", { onSet(edit.rotatedCw()) })
            RoundToolButton(Icons.Rounded.Flip, "Mirror", { onSet(edit.mirrored()) })
            RoundToolButton(Icons.Rounded.Restore, "Reset", { onSet(edit.copy(geometry = Geometry())) })
        }
    }
}

@Composable
private fun TextPanel(edit: VideoEdit, onUpdate: (VideoEdit) -> Unit, onCommit: () -> Unit, onSet: (VideoEdit) -> Unit) {
    val t = edit.text
    Column {
        OutlinedTextField(
            value = t.text,
            onValueChange = { onUpdate(edit.copy(text = t.copy(text = it))) },
            placeholder = { Text("Type a caption", color = Muted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Gold,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                cursorColor = Gold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        LaunchedEffect(t.text) { delay(600); onCommit() }
        Spacer(Modifier.height(8.dp))
        ColorDots(Color(t.color)) { onSet(edit.copy(text = t.copy(color = it.toArgb()))) }
        Spacer(Modifier.height(4.dp))
        LocketSlider(
            label = "Size",
            value = t.size,
            range = 0.03f..0.2f,
            center = 0.03f,
            resetTo = 0.08f,
            format = { "${(it * 100).roundToInt()}" },
            onChange = { onUpdate(edit.copy(text = t.copy(size = it))) },
            onFinished = onCommit,
        )
        Text("Drag the text on the video to place it", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AudioPanel(muted: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Chip(
            if (muted) "Sound removed" else "Sound on",
            selected = muted,
            onClick = onToggle,
            icon = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
        )
        Spacer(Modifier.height(8.dp))
        Text(if (muted) "The saved video will be silent" else "Tap to save the video without sound", color = Muted, fontSize = 12.sp)
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val tenth = (ms % 1000) / 100
    return "%d:%02d.%d".format(s / 60, s % 60, tenth)
}
