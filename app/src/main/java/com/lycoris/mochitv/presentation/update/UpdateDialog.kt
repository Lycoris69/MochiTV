package com.lycoris.mochitv.presentation.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lycoris.mochitv.BuildConfig
import com.lycoris.mochitv.domain.model.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: (url: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector        = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text("Update available")
        },
        text = {
            Column {
                Text(
                    text  = "MochiTV ${info.latestVersion} is available " +
                            "(installed: ${BuildConfig.VERSION_NAME}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // Show only first 300 chars to keep the dialog compact
                        text  = info.releaseNotes.take(300).let {
                            if (info.releaseNotes.length > 300) "$it…" else it
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        },
        confirmButton = {
            // Always open the GitHub release page — user can download from there
            TextButton(onClick = { onDownload(info.releasePageUrl) }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}
