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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.lycoris.mochitv.domain.model.AccentColor
import com.lycoris.mochitv.domain.model.AppFont
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.domain.model.FontScale
import com.lycoris.mochitv.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            item { SectionHeader("Theme") }
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
            item { SectionHeader("Accent Color") }
            item {
                ColorSwatchRow(
                    selected = settings.accentColor,
                    onSelect = viewModel::setAccentColor,
                )
            }

            item { SectionDivider() }

            // ----------------------------------------------------------------
            // Font
            // ----------------------------------------------------------------
            item { SectionHeader("Font") }
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
            // Text size
            // ----------------------------------------------------------------
            item { SectionHeader("Text Size") }
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
            item { SectionHeader("Preview") }
            item { PreviewCard(settings) }

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
                        contentDescription = "Selected",
                        tint               = Color.Black.copy(alpha = 0.7f),
                        modifier           = Modifier.size(20.dp),
                    )
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
            text  = "After two and a half years of training, Naruto returns to Konoha village.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Action", "Adventure", "Shōnen").forEach { genre ->
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
