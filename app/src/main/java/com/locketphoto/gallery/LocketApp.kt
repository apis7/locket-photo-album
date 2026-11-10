package com.locketphoto.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.locketphoto.gallery.thumbs.ThumbFetcher
import com.locketphoto.gallery.thumbs.ThumbKeyer

class LocketApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbKeyer())
                add(ThumbFetcher.Factory(contentResolver))
            }
            .crossfade(180)
            .build()
}
