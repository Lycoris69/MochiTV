package com.lycoris.mochitv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry-point. Implements [ImageLoaderFactory] so Coil uses our
 * Hilt-provided [ImageLoader] (which sets the correct Referer header for
 * voiranime.rip image CDN requests) everywhere in the app.
 */
@HiltAndroidApp
class MochiTVApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
}
