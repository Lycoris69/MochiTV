package com.lycoris.mochitv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.mochitv.data.settings.AppSettingsRepository
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.presentation.navigation.AppNavGraph
import com.lycoris.mochitv.presentation.theme.MochiTVTheme
import com.lycoris.mochitv.presentation.update.UpdateCheckState
import com.lycoris.mochitv.presentation.update.UpdateCheckViewModel
import com.lycoris.mochitv.presentation.update.UpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. Compose owns the entire UI surface;
 * this activity is just the window container.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: AppSettingsRepository

    private val updateVm: UpdateCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
                .value

            val updateState by updateVm.state.collectAsStateWithLifecycle()

            MochiTVTheme(settings = settings) {
                AppNavGraph()

                // Show the update dialog on top of whatever screen is visible.
                if (updateState is UpdateCheckState.Available) {
                    UpdateDialog(
                        info      = (updateState as UpdateCheckState.Available).info,
                        onDismiss = updateVm::dismissUpdate,
                        onDownload = { url ->
                            startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            updateVm.dismissUpdate()
                        },
                    )
                }
            }
        }
    }
}
