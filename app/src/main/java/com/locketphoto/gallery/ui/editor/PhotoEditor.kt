package com.locketphoto.gallery.ui.editor

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.edit.AdjustKind
import com.locketphoto.gallery.edit.AspectChoice
import com.locketphoto.gallery.edit.ColorMath
import com.locketphoto.gallery.edit.CropRect
import com.locketphoto.gallery.edit.Filters
import com.locketphoto.gallery.edit.MarkTool
import com.locketphoto.gallery.edit.MarkupPainter
import com.locketphoto.gallery.edit.MediaSaver
import com.locketphoto.gallery.edit.NPoint
import com.locketphoto.gallery.edit.PhotoEdit
import com.locketphoto.gallery.edit.PhotoRenderer
import com.locketphoto.gallery.edit.Stroke
import com.locketphoto.gallery.edit.TextMark
import com.locketphoto.gallery.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** AGSL runs on Android 13+. Below that, the two tone sliders are hidden. */
internal val hasShaders = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// Keep the formula in sync with ColorMath.toneDelta.
private const val TONE_AGSL = """
uniform shader content;
uniform float hi;
uniform float sh;
half4 main(float2 p) {
    half4 c = content.eval(p);
    float a = max(float(c.a), 0.0001);
    float3 rgb = float3(c.rgb) / a;
    float l = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    float d = sh * (1.0 - l) * (1.0 - l) * 0.3 + hi * l * l * 0.3;
    rgb = clamp(rgb + d, 0.0, 1.0);
    return half4(half3(rgb * a), c.a);
}
"""

private val markColors = listOf(
    Color.White, Color(0xFF111111), Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF32D74B), Color(0xFF0A84FF), Color(0xFFBF5AF2),
)
private val markSizes = listOf(0.006f, 0.012f, 0.024f)

@Composable
internal fun PhotoEditor(
    item: MediaItem,
    requestWrite: (Uri, () -> Unit) -> Unit,
    onDone: (Uri?) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    LaunchedEffect(item.uri) {
        runCatching { withContext(Dispatchers.IO) { PhotoRenderer.decode(context.contentResolver, item.uri, maxSide = 2048) } }
            .onSuccess { preview = it }
            .onFailure { loadError = true }
    }

    val history = remember(item.uri) { EditHistory(PhotoEdit()) }
    val edit = history.current
    var tab by remember { mutableStateOf("adjust") }
    var adjustKind by remember { mutableStateOf(AdjustKind.Brightness) }
    var tool by remember { mutableStateOf(MarkTool.Pen) }
    var markColor by remember { mutableStateOf(Color.White) }
    var markSize by remember { mutableFloatStateOf(markSizes[1]) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveProgress by remember { mutableFloatStateOf(-1f) }
    var textDialog by remember { mutableStateOf<TextDialogState?>(null) }
    val overwriteOk = MediaSaver.canOverwritePhoto(item.mimeType)

    BackHandler(enabled = !saving) { if (history.dirty) askDiscard = true else onCancel() }

    fun runSave(copy: Boolean) {
        val src = preview ?: return
        val snapshot = history.current
        val go = {
            saving = true
            saveProgress = 0f
            scope.launch {
                try {
                    val uri = withContext(Dispatchers.Default) {
                        val full = PhotoRenderer.decode(context.contentResolver, item.uri)
                        val out = PhotoRenderer.render(full, snapshot) { p -> saveProgress = p * 0.85f }
                        if (full !== src) full.recycle()
                        saveProgress = 0.9f
                        val u = withContext(Dispatchers.IO) {
                            if (copy) MediaSaver.savePhotoCopy(context, item, out)
                            else { MediaSaver.overwritePhoto(context, item, out); item.uri }
                        }
                        out.recycle()
                        u
                    }
                    saving = false
                    onDone(uri)
                } catch (e: Throwable) {
                    saving = false
                    Toast.makeText(context, "Couldn't save: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
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
            canSave = history.dirty && !saving,
            onCancel = { if (history.dirty) askDiscard = true else onCancel() },
            onUndo = history::undo,
            onRedo = history::redo,
            onSave = { runSave(copy = false) },
            onSaveCopy = { runSave(copy = true) },
            overwriteAllowed = overwriteOk,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val bmp = preview
            if (bmp != null) {
                PhotoViewport(
                    bmp = bmp,
                    edit = edit,
                    tab = tab,
                    tool = tool,
                    markColor = markColor,
                    markSize = markSize,
                    onUpdate = history::update,
                    onCommit = history::commit,
                    onSet = history::set,
                    onTextRequest = { textDialog = it },
                )
            } else if (loadError) {
                Text("Couldn't open this photo", color = Muted, modifier = Modifier.align(Alignment.Center))
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                    scaleIn(spring(dampingRatio = 0.6f), initialScale = 0.96f)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessHigh))
            },
            label = "panel",
            modifier = Modifier.fillMaxWidth().heightIn(min = 176.dp),
        ) { t ->
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                when (t) {
                    "auto" -> AutoPanel(edit.autoOn) {
                        val src = preview ?: return@AutoPanel
                        if (edit.autoOn) history.set(edit.copy(adjust = edit.autoFrom!!, autoFrom = null))
                        else history.set(edit.copy(adjust = PhotoRenderer.autoEnhance(src).let { a -> if (hasShaders) a else a.copy(highlights = 0f, shadows = 0f) }, autoFrom = edit.adjust))
                    }
                    "crop" -> CropPanel(
                        edit = edit,
                        frameRatio = preview?.let { edit.geometry.frameW(it.width.toFloat(), it.height.toFloat()) / edit.geometry.frameH(it.width.toFloat(), it.height.toFloat()) } ?: 1f,
                        onUpdate = history::update,
                        onCommit = history::commit,
                        onSet = history::set,
                    )
                    "adjust" -> AdjustPanel(edit, adjustKind, { adjustKind = it }, history::update, history::commit)
                    "filters" -> FiltersPanel(preview, edit, history::update, history::commit, history::set)
                    else -> MarkupPanel(tool, { tool = it }, markColor, { markColor = it }, markSize, { markSize = it })
                }
            }
        }
        ToolTabs(
            tabs = listOf(
                ToolTab("auto", "Auto", Icons.Rounded.AutoAwesome),
                ToolTab("crop", "Crop", Icons.Rounded.Crop),
                ToolTab("adjust", "Adjust", Icons.Rounded.Tune),
                ToolTab("filters", "Filters", Icons.Rounded.Filter),
                ToolTab("markup", "Markup", Icons.Rounded.Gesture),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(4.dp))
    }

    textDialog?.let { d ->
        TextEntryDialog(
            initial = d.existing?.text.orEmpty(),
            onDismiss = { textDialog = null },
            onConfirm = { text ->
                textDialog = null
                val texts = edit.markup.texts.toMutableList()
                if (d.existing != null) {
                    val i = texts.indexOf(d.existing)
                    if (i >= 0) { if (text.isBlank()) texts.removeAt(i) else texts[i] = d.existing.copy(text = text) }
                } else if (text.isNotBlank()) {
                    texts += TextMark(text, d.at, markColor.toArgb(), markSize * 5f + 0.03f)
                }
                history.set(edit.copy(markup = edit.markup.copy(texts = texts)))
            },
        )
    }
    if (askDiscard) DiscardDialog(onDiscard = { askDiscard = false; onCancel() }, onKeep = { askDiscard = false })
    if (saving) SavingOverlay("Saving photo", saveProgress, null)
}

internal data class TextDialogState(val at: NPoint, val existing: TextMark?)

@Composable
private fun PhotoViewport(
    bmp: Bitmap,
    edit: PhotoEdit,
    tab: String,
    tool: MarkTool,
    markColor: Color,
    markSize: Float,
    onUpdate: (PhotoEdit) -> Unit,
    onCommit: () -> Unit,
    onSet: (PhotoEdit) -> Unit,
    onTextRequest: (TextDialogState) -> Unit,
) {
    val density = LocalDensity.current
    var holdOriginal by remember { mutableStateOf(false) }
    val liveStroke = remember { mutableStateListOf<NPoint>() }
    val painter = remember { MarkupPainter() }
    val editNow by rememberUpdatedState(edit)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vw = with(density) { maxWidth.toPx() }
        val vh = with(density) { maxHeight.toPx() }
        val pad = with(density) { (if (tab == "crop") 28.dp else 12.dp).toPx() }
        val sw = bmp.width.toFloat()
        val sh = bmp.height.toFloat()
        val g = edit.geometry
        val fw = g.frameW(sw, sh)
        val fh = g.frameH(sw, sh)
        val cropMode = tab == "crop"
        val regionX = if (cropMode) 0f else g.crop.left * fw
        val regionY = if (cropMode) 0f else g.crop.top * fh
        val regionW = if (cropMode) fw else g.crop.width * fw
        val regionH = if (cropMode) fh else g.crop.height * fh
        val fitTarget = min((vw - 2 * pad) / regionW, (vh - 2 * pad) / regionH).coerceAtLeast(0.0001f)
        val bouncy = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        val fit by animateFloatAsState(fitTarget, bouncy, label = "fit")
        val offX by animateFloatAsState((vw - regionW * fitTarget) / 2f - regionX * fitTarget, bouncy, label = "offX")
        val offY by animateFloatAsState((vh - regionH * fitTarget) / 2f - regionY * fitTarget, bouncy, label = "offY")
        // Crop window in view pixels (for clipping) and the full frame (for the crop tool).
        val clipL = offX + (if (cropMode) 0f else g.crop.left * fw) * fit
        val clipT = offY + (if (cropMode) 0f else g.crop.top * fh) * fit
        val clipR = clipL + regionW * fit
        val clipB = clipT + regionH * fit

        val matrix = remember(edit.filterId, edit.filterIntensity, edit.adjust) { ColorMath.matrix(edit.adjust, edit.filterId, edit.filterIntensity) }
        val paint = remember(matrix) {
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                if (!ColorMath.isIdentity(matrix)) colorFilter = ColorMatrixColorFilter(matrix)
            }
        }
        val plainPaint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
        val shader = remember { if (hasShaders) RuntimeShader(TONE_AGSL) else null }
        val tone = edit.adjust.hasTone && !holdOriginal && hasShaders

        fun toFrame(p: Offset) = NPoint(((p.x - offX) / fit) / fw, ((p.y - offY) / fit) / fh)

        // 1. The picture (tone shader applies to this layer only).
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = if (tone && shader != null && Build.VERSION.SDK_INT >= 33) {
                        shader.setFloatUniform("hi", edit.adjust.highlights)
                        shader.setFloatUniform("sh", edit.adjust.shadows)
                        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                    } else null
                },
        ) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                if (holdOriginal) {
                    val k = min((vw - 2 * pad) / sw, (vh - 2 * pad) / sh)
                    val m = android.graphics.Matrix().apply { postScale(k, k); postTranslate((vw - sw * k) / 2f, (vh - sh * k) / 2f) }
                    nc.drawBitmap(bmp, m, plainPaint)
                    return@drawIntoCanvas
                }
                nc.save()
                nc.clipRect(clipL, clipT, clipR, clipB)
                val m = g.frameMatrix(sw, sh).apply { postScale(fit, fit); postTranslate(offX, offY) }
                nc.drawBitmap(bmp, m, paint)
                nc.restore()
            }
        }
        // 2. Vignette + markup on top.
        if (!holdOriginal) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.clipRect(clipL, clipT, clipR, clipB)
                    if (!cropMode && edit.adjust.vignette != 0f) {
                        nc.save()
                        nc.translate(clipL, clipT)
                        PhotoRenderer.drawVignette(nc, clipR - clipL, clipB - clipT, edit.adjust.vignette)
                        nc.restore()
                    }
                    nc.translate(offX, offY)
                    nc.scale(fit, fit)
                    painter.draw(nc, edit.markup, fw, fh)
                    if (liveStroke.isNotEmpty()) {
                        painter.drawStroke(nc, Stroke(liveStroke.toList(), markColor.toArgb(), strokeWidth(tool, markSize), tool == MarkTool.Highlighter), fw, fh, min(fw, fh))
                    }
                    nc.restore()
                }
            }
        }
        // 3. Gestures for the active tool.
        when {
            cropMode -> CropOverlay(
                frame = frameRect(offX, offY, fw * fit, fh * fit),
                crop = g.crop,
                ratio = g.aspect.ratio(fw / fh),
                onChange = { onUpdate(editNow.copy(geometry = editNow.geometry.copy(crop = it))) },
                onEnd = onCommit,
            )
            tab == "markup" -> Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tool, markColor, markSize, fit, offX, offY) {
                        val unit = min(fw, fh)
                        fun textAt(p: NPoint): TextMark? = editNow.markup.texts.lastOrNull { t ->
                            val (hw, hh) = painter.textHalfSize(t, unit)
                            kotlin.math.abs((p.x - t.at.x) * fw) < hw + 12f && kotlin.math.abs((p.y - t.at.y) * fh) < hh + 12f
                        }
                        fun erase(p: NPoint) {
                            val r = 0.03f * unit
                            val m = editNow.markup
                            val strokes = m.strokes.filterNot { s -> s.points.any { q -> hypot((q.x - p.x) * fw, (q.y - p.y) * fh) < r + s.width * unit / 2f } }
                            val texts = m.texts.filterNot { it == textAt(p) }
                            if (strokes.size != m.strokes.size || texts.size != m.texts.size) onUpdate(editNow.copy(markup = m.copy(strokes = strokes, texts = texts)))
                        }
                        when (tool) {
                            MarkTool.Pen, MarkTool.Highlighter -> detectDragGestures(
                                onDragStart = { liveStroke.clear(); liveStroke += toFrame(it) },
                                onDragEnd = {
                                    val s = Stroke(liveStroke.toList(), markColor.toArgb(), strokeWidth(tool, markSize), tool == MarkTool.Highlighter)
                                    liveStroke.clear()
                                    onSet(editNow.copy(markup = editNow.markup.copy(strokes = editNow.markup.strokes + s)))
                                },
                                onDragCancel = { liveStroke.clear() },
                            ) { change, _ -> change.consume(); liveStroke += toFrame(change.position) }
                            MarkTool.Eraser -> detectDragGestures(
                                onDragStart = { erase(toFrame(it)) },
                                onDragEnd = onCommit,
                                onDragCancel = onCommit,
                            ) { change, _ -> change.consume(); erase(toFrame(change.position)) }
                            MarkTool.Text -> {
                                var dragging: TextMark? = null
                                detectDragGestures(
                                    onDragStart = { dragging = textAt(toFrame(it)) },
                                    onDragEnd = { if (dragging != null) onCommit(); dragging = null },
                                    onDragCancel = { if (dragging != null) onCommit(); dragging = null },
                                ) { change, d ->
                                    val t = dragging ?: return@detectDragGestures
                                    change.consume()
                                    val moved = t.copy(at = NPoint(t.at.x + d.x / fit / fw, t.at.y + d.y / fit / fh))
                                    val texts = editNow.markup.texts.map { if (it == t) moved else it }
                                    dragging = moved
                                    onUpdate(editNow.copy(markup = editNow.markup.copy(texts = texts)))
                                }
                            }
                        }
                    }
                    .pointerInput(tool, fit, offX, offY) {
                        if (tool == MarkTool.Text) {
                            detectTapGestures { p ->
                                val n = toFrame(p)
                                val unit = min(fw, fh)
                                val hit = editNow.markup.texts.lastOrNull { t ->
                                    val (hw, hh) = painter.textHalfSize(t, unit)
                                    kotlin.math.abs((n.x - t.at.x) * fw) < hw + 12f && kotlin.math.abs((n.y - t.at.y) * fh) < hh + 12f
                                }
                                onTextRequest(TextDialogState(n, hit))
                            }
                        } else if (tool == MarkTool.Eraser) {
                            detectTapGestures { p ->
                                val n = toFrame(p)
                                val unit = min(fw, fh)
                                val r = 0.03f * unit
                                val m = editNow.markup
                                val strokes = m.strokes.filterNot { s -> s.points.any { q -> hypot((q.x - n.x) * fw, (q.y - n.y) * fh) < r + s.width * unit / 2f } }
                                val texts = m.texts.filterNot { t ->
                                    val (hw, hh) = painter.textHalfSize(t, unit)
                                    kotlin.math.abs((n.x - t.at.x) * fw) < hw && kotlin.math.abs((n.y - t.at.y) * fh) < hh
                                }
                                onSet(editNow.copy(markup = m.copy(strokes = strokes, texts = texts)))
                            }
                        }
                    },
            )
            else -> Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holdOriginal = true
                        tryAwaitRelease()
                        holdOriginal = false
                    })
                },
            )
        }
        if (holdOriginal) {
            Text(
                "Original",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

private fun strokeWidth(tool: MarkTool, size: Float) = if (tool == MarkTool.Highlighter) size * 3f else size

@Composable
private fun AutoPanel(on: Boolean, onToggle: () -> Unit) {
    val s by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "autoScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .scale(s)
                .clip(RoundedCornerShape(50))
                .background(if (on) Gold else Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 26.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Rounded.AutoFixHigh, null, tint = if (on) Color(0xFF231A06) else Gold)
            Spacer(Modifier.width(10.dp))
            Text(if (on) "Enhanced" else "Auto enhance", color = if (on) Color(0xFF231A06) else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (on) "Tap again to undo. Fine-tune in Adjust." else "One tap: balanced light, contrast and colour.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CropPanel(edit: PhotoEdit, frameRatio: Float, onUpdate: (PhotoEdit) -> Unit, onCommit: () -> Unit, onSet: (PhotoEdit) -> Unit) {
    val g = edit.geometry
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (a in AspectChoice.entries) {
                Chip(a.label, g.aspect == a, onClick = {
                    val ratio = a.ratio(frameRatio)
                    val crop = if (ratio == null) g.crop else CropRect.centered(ratio, frameRatio, 1f)
                    onSet(edit.copy(geometry = g.copy(aspect = a, crop = crop)))
                })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundToolButton(Icons.Rounded.RotateRight, "Rotate", { onSet(edit.rotatedCw()) })
            Spacer(Modifier.width(10.dp))
            RoundToolButton(Icons.Rounded.Flip, "Mirror", { onSet(edit.mirrored()) })
            Spacer(Modifier.width(14.dp))
            LocketSlider(
                label = "Straighten",
                value = g.straighten,
                range = -45f..45f,
                onChange = { onUpdate(edit.copy(geometry = g.copy(straighten = it))) },
                onFinished = onCommit,
                format = { "%.1f°".format(it) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            RoundToolButton(Icons.Rounded.Restore, "Reset", { onSet(edit.copy(geometry = com.locketphoto.gallery.edit.Geometry())) })
        }
    }
}

@Composable
private fun AdjustPanel(edit: PhotoEdit, kind: AdjustKind, onKind: (AdjustKind) -> Unit, onUpdate: (PhotoEdit) -> Unit, onCommit: () -> Unit) {
    val kinds = AdjustKind.entries.filter { hasShaders || !it.needsShader }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (k in kinds) {
                val v = edit.adjust.get(k)
                Chip(if (v != 0f) "${k.label} •" else k.label, k == kind, onClick = { onKind(k) })
            }
        }
        Spacer(Modifier.height(10.dp))
        LocketSlider(
            label = kind.label,
            value = edit.adjust.get(kind),
            onChange = { onUpdate(edit.copy(adjust = edit.adjust.with(kind, it))) },
            onFinished = onCommit,
        )
    }
}

@Composable
private fun FiltersPanel(preview: Bitmap?, edit: PhotoEdit, onUpdate: (PhotoEdit) -> Unit, onCommit: () -> Unit, onSet: (PhotoEdit) -> Unit) {
    val thumb = remember(preview) {
        preview?.let { b ->
            val side = min(b.width, b.height)
            val sq = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
            Bitmap.createScaledBitmap(sq, 180, 180, true).asImageBitmap()
        }
    }
    Column {
        LocketSlider(
            label = "Intensity",
            value = edit.filterIntensity,
            range = 0f..1f,
            center = 0f,
            resetTo = 1f,
            enabled = edit.filterId != Filters.none.id,
            format = { "${(it * 100).roundToInt()}" },
            onChange = { onUpdate(edit.copy(filterIntensity = it)) },
            onFinished = onCommit,
        )
        Spacer(Modifier.height(6.dp))
        FilterStrip(thumb, edit.filterId) { id -> onSet(edit.copy(filterId = id, filterIntensity = 1f)) }
    }
}

@Composable
internal fun FilterStrip(thumb: androidx.compose.ui.graphics.ImageBitmap?, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Filters.all, key = { it.id }) { f ->
            val on = f.id == selected
            val s by animateFloatAsState(if (on) 1.08f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "fScale")
            val cf = remember(f.id) {
                val m = f.build()
                if (ColorMath.isIdentity(m)) null else ColorFilter.colorMatrix(ComposeColorMatrix(m.array))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(f.id) }) {
                Box(
                    Modifier
                        .scale(s)
                        .size(62.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (on) 2.dp else 0.dp, if (on) Gold else Color.Transparent, RoundedCornerShape(14.dp))
                        .background(Panel),
                ) {
                    if (thumb != null) {
                        Image(thumb, f.name, contentScale = ContentScale.Crop, colorFilter = cf, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(f.name, color = if (on) Gold else Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MarkupPanel(
    tool: MarkTool,
    onTool: (MarkTool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    size: Float,
    onSize: (Float) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundToolButton(Icons.Rounded.Edit, "Pen", { onTool(MarkTool.Pen) }, active = tool == MarkTool.Pen)
            RoundToolButton(Icons.Rounded.BorderColor, "Highlight", { onTool(MarkTool.Highlighter) }, active = tool == MarkTool.Highlighter)
            RoundToolButton(Icons.Rounded.TextFields, "Text", { onTool(MarkTool.Text) }, active = tool == MarkTool.Text)
            RoundToolButton(Icons.Rounded.CleaningServices, "Eraser", { onTool(MarkTool.Eraser) }, active = tool == MarkTool.Eraser)
            Spacer(Modifier.weight(1f))
            for (s in markSizes) {
                val on = s == size
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp).clip(CircleShape).background(if (on) Gold.copy(alpha = 0.22f) else Color.Transparent).clickable { onSize(s) },
                ) { Box(Modifier.size((4 + s * 600).dp.coerceAtMost(18.dp)).clip(CircleShape).background(if (on) Gold else Color.White)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        ColorDots(color, onColor)
        if (tool == MarkTool.Text) {
            Spacer(Modifier.height(4.dp))
            Text("Tap to add text · drag text to move · tap text to edit", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ColorDots(color: Color, onColor: (Color) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        for (c in markColors) {
            val on = c == color
            val s by animateFloatAsState(if (on) 1.2f else 1f, spring(dampingRatio = 0.45f), label = "dot")
            Box(
                Modifier
                    .scale(s)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (on) 2.5.dp else 1.dp, if (on) Gold else Color.White.copy(alpha = 0.3f), CircleShape)
                    .clickable { onColor(c) },
            )
        }
    }
}
