package com.lycoris.mochitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.mochitv.data.settings.AppSettingsRepository
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.presentation.navigation.AppNavGraph
import com.lycoris.mochitv.presentation.theme.MochiTVTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. Compose owns the entire UI surface;
 * this activity is just the window container.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
                .value

            MochiTVTheme(settings = settings) {
                AppNavGraph()
            }
        }
    }
}
