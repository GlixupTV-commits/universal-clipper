package com.glixup.universalclipper

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UniversalClipperApp() }
    }
}

private data class VideoInfo(
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

@Composable
fun UniversalClipperApp() {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf&lt;Uri?&gt;(null) }
    var videoInfo by remember { mutableStateOf&lt;VideoInfo?&gt;(null) }
    var status by remember { mutableStateOf("Choose a video stored on your device to begin.") }
    var loading by remember { mutableStateOf(false) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -&gt;
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers do not offer persistable grants.
            }
            selectedUri = uri
            videoInfo = null
            status = "Reading video information…"
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        loading = true
        try {
            val info = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -&gt;
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() &amp;&amp; nameIndex &gt;= 0) cursor.getString(nameIndex) else null
                } ?: "Selected video"

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    VideoInfo(
                        name = name,
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    )
                } finally {
                    retriever.release()
                }
            }
            videoInfo = info
            status = "Video imported. Review its details below."
        } catch (error: Exception) {
            status = "Couldn't read this video. Try another file. (${error.javaClass.simpleName})"
        } finally {
            loading = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("UNIVERSAL CLIPPER", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Your on-device video clipping workspace")
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                    Text("Import a video")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (loading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Please wait…")
                }
                videoInfo?.let { info -&gt;
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(info.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Duration: ${formatDuration(info.durationMs)}")
                            Text("Resolution: ${info.width} × ${info.height}")
                            Text("Source: local device storage")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours &gt; 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
