package com.maysk.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maysk.music.data.FileOps
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtractAudioScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var lastName by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        lastName = name
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                FileOps.extractAudioFromVideo(ctx, uri, name)
            }
            busy = false
            if (result.error != null) {
                a.toast(result.error)
            } else {
                a.toast("Аудио сохранено в Music/Maysk/")
                FileOps.rescanFiles(ctx, result.scan)
                a.container.library.rescan(debounceMs = 800)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Извлечь аудио из видео", onBack = { a.nav.pop() })

        if (busy) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(16.dp))
                Text("Извлечение…", color = c.text)
                lastName?.let {
                    Text(it, color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            EmptyState(
                Icons.Rounded.VideoFile,
                "Аудио из видео",
                "Выберите видео (MP4, MKV, 3GP, WebM…). Аудиодорожка сохранится как .m4a в Music/Maysk/ и появится в библиотеке.",
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { picker.launch("video/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text("Выбрать видео")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Работает без интернета. Исходное видео не изменяется.",
                color = c.textDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
