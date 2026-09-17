package com.example.core.design

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.AuraApplication
import com.example.core.download.DownloadStatus
import com.example.core.model.Track

@Composable
fun DownloadButton(track: Track, modifier: Modifier = Modifier) {
    val application = LocalContext.current.applicationContext as? AuraApplication ?: return
    val repository = application.container.downloadRepository
    val downloads by repository.downloads.collectAsStateWithLifecycle()
    val entry = downloads[track.id]
    var showDialog by remember(track.id) { mutableStateOf(false) }
    val complete = entry?.status == DownloadStatus.COMPLETE
    val active = entry?.isActive == true
    val failed = entry?.status == DownloadStatus.FAILED
    val label = when {
        complete -> "Downloaded: ${track.title}. Remove download"
        failed -> "Download failed: ${track.title}. Retry download"
        entry?.status == DownloadStatus.PAUSED -> "Download paused: ${track.title}. Waiting for connection. Tap to cancel"
        active -> "Downloading ${track.title}. Tap to cancel"
        else -> "Download ${track.title}"
    }
    IconButton(
        onClick = { if (entry == null) repository.download(track) else showDialog = true },
        modifier = modifier.testTag("download_button_${track.id}").semantics { contentDescription = label }
    ) {
        if (active) {
            val total = entry?.totalBytes ?: -1
            if (total > 0) {
                CircularProgressIndicator(
                    progress = { ((entry?.bytesDownloaded ?: 0).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            Icon(when {
                complete -> Icons.Default.DownloadDone
                failed -> Icons.Default.ErrorOutline
                else -> Icons.Default.Download
            }, contentDescription = null)
        }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(when { complete -> "Remove download?"; failed -> "Download failed"; else -> "Cancel download?" }) },
            text = { Text(when {
                complete -> "Remove ${track.title} from this phone to free up space? You can download it again later."
                failed -> entry?.error ?: "Check your connection and phone storage, then retry."
                else -> "Stop downloading ${track.title}? You can download it again later."
            }) },
            confirmButton = {
                TextButton(onClick = {
                    if (failed) repository.download(track) else repository.remove(track.id)
                    showDialog = false
                }) { Text(if (failed) "Retry" else if (complete) "Remove" else "Cancel download") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Keep") }
                if (failed) TextButton(onClick = {
                    repository.remove(track.id)
                    showDialog = false
                }) { Text("Remove") }
            }
        )
    }
}
