package com.maysk.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.EmptyState
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAYSK_MUSIC_REL = "Music/Maysk"

@Composable
fun ExtractAudioScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var saveName by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var outFormat by remember { mutableStateOf("mp3") }
    var lastSavedUri by remember { mutableStateOf<String?>(null) }

    fun openFolder(saved: String?) {
        try {
            if (saved != null && saved.startsWith("content:")) {
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(saved), "audio/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(Intent.createChooser(view, "Открыть файл"))
                return
            }
            val folderUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:$MAYSK_MUSIC_REL",
            )
            val openFolder = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ctx.startActivity(openFolder); return } catch (_: Exception) {}
            a.toast("Папка: $MAYSK_MUSIC_REL")
        } catch (e: Exception) {
            a.toast("Не удалось открыть: ${e.message}")
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            ctx.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.substringBeforeLast('.') ?: "Из видео"
        pendingUri = uri
        saveName = name.take(50)
        showDialog = true
    }

    fun doExtract() {
        val uri = pendingUri ?: return
        showDialog = false
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                FileOps.extractAudioFromVideo(ctx, uri, saveName, outFormat)
            }
            busy = false
            pendingUri = null
            if (result.error != null) {
                a.toast(result.error)
            } else {
                lastSavedUri = result.scan.firstOrNull()
                a.toast("Сохранено в $MAYSK_MUSIC_REL")
                FileOps.rescanFiles(ctx, result.scan)
                a.container.library.rescan(debounceMs = 800)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Извлечь аудио из видео", onBack = { a.nav.pop() })

        if (busy) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(16.dp))
                Text("Извлечение…", color = c.text)
            }
        } else {
            EmptyState(
                Icons.Rounded.VideoFile,
                "Аудио из видео",
                "Выберите видео. Перед сохранением можно задать название. Файл → $MAYSK_MUSIC_REL",
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { picker.launch("video/*") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) { Text("Выбрать видео") }

            if (lastSavedUri != null) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { openFolder(lastSavedUri) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) { Text("Открыть папку / файл") }
                OutlinedButton(
                    onClick = {
                        a.nav.push(
                            Route.FolderR(
                                File(Environment.getExternalStorageDirectory(), MAYSK_MUSIC_REL).absolutePath,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                ) { Text("Папка в «Моя музыка»") }
            } else {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { openFolder(null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) { Text("Открыть папку $MAYSK_MUSIC_REL") }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) { showDialog = false; pendingUri = null } },
            title = { Text("Сохранить аудио") },
            text = {
                Column {
                    Text("Название")
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Формат")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outFormat == "mp3",
                            onClick = { outFormat = "mp3" },
                            label = { Text("MP3") },
                        )
                        FilterChip(
                            selected = outFormat == "m4a",
                            onClick = { outFormat = "m4a" },
                            label = { Text("M4A (AAC)") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Файл сохранится в Music/Maysk/.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textDim,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { doExtract() }, enabled = saveName.isNotBlank()) { Text("Извлечь") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false; pendingUri = null }) { Text("Отмена") }
            },
        )
    }
}
