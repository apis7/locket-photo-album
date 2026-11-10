package com.locketphoto.gallery.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/** Set by the app root so any screen can take part in the grid <-> viewer zoom. */
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this box as "the same photo" across screens, so it flies from its grid cell
 * to full screen and back.
 */
@Composable
fun Modifier.sharedMedia(id: Long, shape: Shape = RectangleShape, enabled: Boolean = true): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (shared == null || nav == null || !enabled) return this
    return with(shared) {
        this@sharedMedia.sharedBounds(
            sharedContentState = rememberSharedContentState("media-$id"),
            animatedVisibilityScope = nav,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
