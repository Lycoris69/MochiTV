package com.lycoris.mochitv.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lycoris.mochitv.BuildConfig
import com.lycoris.mochitv.domain.model.AccentColor
import com.lycoris.mochitv.domain.model.AppFont
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.domain.model.FontScale
import com.lycoris.mochitv.domain.model.ThemeMode
import com.lycoris.mochitv.presentation.update.UpdateCheckState
import com.lycoris.mochitv.presentation.update.UpdateCheckViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateVm: UpdateCheckViewModel = hiltViewModel(),
) {
    val settings     by viewModel.settings.collectAsStateWithLifecycle()
    val updateState  by updateVm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                expandedHeight = 30.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start  = 16.dp,
                end    = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ----------------------------------------------------------------
            // Theme
            // ----------------------------------------------------------------
            item { SectionHeader("Thème") }
            item {
                ChipRow(
                    items    = ThemeMode.entries,
                    selected = settings.themeMode,
                    label    = { it.label },
                    onSelect = viewModel::setThemeMode,
                )
            }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // Accent colour
            // ----------------------------------------------------------------
            item { SectionHeader("Couleur d'accent") }
            item {
                ColorSwatchRow(
                    selected = settings.accentColor,
                    onSelect = viewModel::setAccentColor,
                )
            }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // Police
            // ----------------------------------------------------------------
            item { SectionHeader("Police") }
            item {
                ChipRow(
                    items    = AppFont.entries,
                    selected = settings.appFont,
                    label    = { it.label },
                    onSelect = viewModel::setAppFont,
                )
            }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // Taille du texte
            // ----------------------------------------------------------------
            item { SectionHeader("Taille du texte") }
            item {
                ChipRow(
                    items    = FontScale.entries,
                    selected = settings.fontScale,
                    label    = { it.label },
                    onSelect = viewModel::setFontScale,
                )
            }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // Preview card
            // ----------------------------------------------------------------
            item { SectionHeader("Aperçu") }
            item { PreviewCard(settings) }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // À propos / mise à jour
            // ----------------------------------------------------------------
            item { SectionHeader("À propos") }
            item {
                UpdateRow(
                    state    = updateState,
                    onCheck  = { updateVm.checkForUpdate(silent = false) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick  = { onSelect(item) },
                label    = { Text(label(item)) },
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(
    selected: AccentColor,
    onSelect: (AccentColor) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        AccentColor.entries.forEach { color ->
            val isSelected = color == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.darkPrimary)
                    .then(
                        if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(color) },
            ) {
                if (isSelected) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = "Sélectionné",
                        tint               = Color.Black.copy(alpha = 0.7f),
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(
    state: UpdateCheckState,
    onCheck: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text  = "MochiTV ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text  = when (state) {
                    UpdateCheckState.Idle, UpdateCheckState.Checking -> "Vérification des mises à jour…"
                    UpdateCheckState.UpToDate -> "L'application est à jour ✓"
                    is UpdateCheckState.Available ->
                        "Version ${state.info.latestVersion} disponible !"
                    is UpdateCheckState.Error -> "Erreur : ${state.message}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (state) {
                    is UpdateCheckState.Available -> MaterialTheme.colorScheme.primary
                    is UpdateCheckState.Error     -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
            )
        }

        when (state) {
            UpdateCheckState.Checking -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            is UpdateCheckState.Available -> {
                Button(onClick = onCheck) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mettre à jour")
                }
            }
            else -> {
                OutlinedButton(
                    onClick  = onCheck,
                    enabled  = state != UpdateCheckState.Checking,
                ) {
                    Text("Vérifier")
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(settings: AppSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text  = "Naruto Shippuden",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Après deux ans et demi d'entraînement, Naruto revient au village de Konoha.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Action", "Aventure", "Shōnen").forEach { genre ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text  = genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
