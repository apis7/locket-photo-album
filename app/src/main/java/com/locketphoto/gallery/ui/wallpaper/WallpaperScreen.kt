package com.locketphoto.gallery.ui.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private enum class Target(val label: String, val flags: Int) {
    Home("Home", WallpaperManager.FLAG_SYSTEM),
    Lock("Lock", WallpaperManager.FLAG_LOCK),
    Both("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** Pan & zoom the photo inside a phone-shaped frame, then set it as home/lock wallpaper. */
@Composable
fun WallpaperScreen(item: MediaItem, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screen = remember { screenSize(context) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var target by remember { mutableIntStateOf(Target.Both.ordinal) }
    var busy by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var framePx by remember { mutableFloatStateOf(1f) }

    BackHandler(enabled = !busy) { onDone(null) }
    LaunchedEffect(item.id) {
        preview = withContext(Dispatchers.IO) { decode(context, item, maxSide = 1600) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F0E12)).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { onDone(null) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Text("Set as wallpaper", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            // Frame has the phone's exact aspect ratio.
            val ratio = screen.width.toFloat() / screen.height
            val frameH = if (maxWidth / maxHeight > ratio) maxHeight else maxWidth / ratio
            val frameW = frameH * ratio
            val bmp = preview
            Box(
                Modifier
                    .size(frameW, frameH)
                    .shadow(20.dp, RoundedCornerShape(28.dp))
                    .graphicsLayer { clip = true; shape = RoundedCornerShape(28.dp) }
                    .background(Color.Black),
            ) {
                if (bmp == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    val image: ImageBitmap = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    offset = clampOffset(offset + pan, size.width.toFloat(), size.height.toFloat(), bmp, zoom)
                                }
                            },
                    ) {
                        framePx = size.width
                        val s = coverScale(size.width, size.height, bmp) * zoom
                        offset = clampOffset(offset, size.width, size.height, bmp, zoom)
                        val w = bmp.width * s
                        val h = bmp.height * s
                        val left = size.width / 2 - w / 2 + offset.x
                        val top = size.height / 2 - h / 2 + offset.y
                        drawImage(
                            image,
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                    }
                    if (target != Target.Home.ordinal) {
                        // Rough lock-screen clock so you can judge the framing.
                        Text(
                            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm")),
                            color = Color.White,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = frameH * 0.12f),
                        )
                    }
                }
            }
        }

        Text("Pinch and drag to frame it", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp)) {
            Target.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = target == i,
                    onClick = { target = i },
                    shape = SegmentedButtonDefaults.itemShape(i, Target.entries.size),
                ) { Text(t.label) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = preview != null && !busy,
            onClick = {
                val bmp = preview ?: return@Button
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            // Offsets were measured in preview-frame pixels; scale to screen pixels.
                            val k = screen.width / framePx
                            apply(context, item, bmp, zoom, Offset(offset.x * k, offset.y * k), screen, Target.entries[target].flags)
                        }.isSuccess
                    }
                    busy = false
                    onDone(if (ok) "Wallpaper set" else "Couldn't set wallpaper")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp) else Text("Set wallpaper")
        }
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(16.dp))
    }
}

private fun coverScale(w: Float, h: Float, b: Bitmap) = max(w / b.width, h / b.height)

private fun clampOffset(o: Offset, w: Float, h: Float, b: Bitmap, zoom: Float): Offset {
    val s = coverScale(w, h, b) * zoom
    val maxX = ((b.width * s - w) / 2).coerceAtLeast(0f)
    val maxY = ((b.height * s - h) / 2).coerceAtLeast(0f)
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}

private fun screenSize(context: Context): IntSize {
    val wm = context.getSystemService(WindowManager::class.java)
    val b = wm.maximumWindowMetrics.bounds
    return IntSize(minOf(b.width(), b.height()), max(b.width(), b.height()))
}

/** Decodes with EXIF rotation applied, longest side at most [maxSide]. */
private fun decode(context: Context, item: MediaItem, maxSide: Int): Bitmap {
    val src = ImageDecoder.createSource(context.contentResolver, item.uri)
    return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val k = (maxSide.toFloat() / max(w, h)).coerceAtMost(1f)
        decoder.setTargetSize((w * k).roundToInt().coerceAtLeast(1), (h * k).roundToInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

/**
 * Maps the on-screen framing (done on the small preview, in a frame shaped like the screen)
 * to a crop of a larger decode, and hands it to WallpaperManager.
 */
private fun apply(context: Context, item: MediaItem, preview: Bitmap, zoom: Float, offset: Offset, screen: IntSize, flags: Int) {
    val full = decode(context, item, maxSide = max(screen.height * 2, 2400))
    // Work in a virtual frame the size of the screen ([offset] is already in screen pixels).
    val frameW = screen.width.toFloat()
    val frameH = screen.height.toFloat()
    val s = coverScale(frameW, frameH, preview) * zoom
    val o = clampOffset(offset, frameW, frameH, preview, zoom)
    val left = (frameW / 2 - preview.width * s / 2 + o.x)
    val top = (frameH / 2 - preview.height * s / 2 + o.y)
    val k = full.width.toFloat() / preview.width
    val cropL = (-left / s * k).roundToInt().coerceIn(0, full.width - 1)
    val cropT = (-top / s * k).roundToInt().coerceIn(0, full.height - 1)
    val cropW = (frameW / s * k).roundToInt().coerceAtMost(full.width - cropL)
    val cropH = (frameH / s * k).roundToInt().coerceAtMost(full.height - cropT)
    WallpaperManager.getInstance(context).setBitmap(full, Rect(cropL, cropT, cropL + cropW, cropT + cropH), true, flags)
    full.recycle()
}
