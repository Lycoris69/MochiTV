package com.lycoris.mochitv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry-point. The [HiltAndroidApp] annotation triggers Hilt's
 * code generation and attaches the top-level component to the process lifecycle.
 */
@HiltAndroidApp
class MochiTVApp : Application()
