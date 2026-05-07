package com.grow.gallery.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.size.Precision
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            // No global crossfade — avoids animation overhead during fast scroll
            .crossfade(false)
            .allowRgb565(true)
            .precision(Precision.INEXACT)
            .respectCacheHeaders(false)
            .build()
    }
}
