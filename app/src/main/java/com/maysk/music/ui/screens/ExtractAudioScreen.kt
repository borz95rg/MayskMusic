package com.maysk.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var lastName by remember { mutableStateOf<String?>(null) }
    var lastSavedUri by remember { mutableStateOf<String?>(null) }
    var lastSavedPath by remember { mutableStateOf<String?>(null) }

    fun openInFileManager(saved: String?) {
        try {
            // 1) Если есть content:// URI — открыть сам файл
            if (saved != null && saved.startsWith("content:")) {
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(saved), "audio/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(Intent.createChooser(view, "Открыть файл"))
                return
            }
            // 2) Папка Music/Maysk через Documents UI
            val docId = "primary:$MAYSK_MUSIC_REL"
            val folderUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                docId,
            )
            val openFolder = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(openFolder)
                return
            } catch (_: Exception) { }
            // 3) file:// путь
            val dir = File(Environment.getExternalStorageDirectory(), MAYSK_MUSIC_REL)
            if (dir.exists()) {
                val fileUri = Uri.parse(dir.absolutePath)
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    ctx.startActivity(i)
                    return
                } catch (_: Exception) { }
            }
            a.toast("Папка: $MAYSK_MUSIC_REL")
        } catch (e: Exception) {
            a.toast("Не удалось открыть: ${e.message}")
        }
    }

    fun openInAppFolders() {
        // В приложении: экран папок → путь Maysk
        val path = lastSavedPath
            ?: File(Environment.getExternalStorageDirectory(), MAYSK_MUSIC_REL).absolutePath
        a.nav.push(Route.FolderR(path))
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        lastName = name
        busy = true
        lastSavedUri = null
        lastSavedPath = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                FileOps.extractAudioFromVideo(ctx, uri, name)
            }
            busy = false
            if (result.error != null) {
                a.toast(result.error)
            } else {
                val saved = result.scan.firstOrNull()
                lastSavedUri = saved
                if (saved != null && !saved.startsWith("content:")) {
                    lastSavedPath = File(saved).parent
                } else {
                    lastSavedPath = File(Environment.getExternalStorageDirectory(), MAYSK_MUSIC_REL).absolutePath
                }
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
                lastName?.let {
                    Text(it, color = c.textDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            EmptyState(
                Icons.Rounded.VideoFile,
                "Аудио из видео",
                "Выберите видео (MP4, MKV, 3GP…). Аудио сохранится как .m4a в папку $MAYSK_MUSIC_REL и появится в библиотеке.",
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { picker.launch("video/*") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(if (lastSavedUri == null) "Выбрать видео" else "Выбрать другое видео")
            }

            if (lastSavedUri != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Готово · $MAYSK_MUSIC_REL" + (lastName?.let { "\n$it" } ?: ""),
                    color = c.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { openInFileManager(lastSavedUri) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text("Открыть папку / файл")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openInAppFolders() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text("Папка в «Моя музыка»")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        a.nav.pop()
                        a.nav.selectTab(4) // Моя музыка
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text("Перейти в Моя музыка")
                }
            } else {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        openInFileManager(null)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text("Открыть папку $MAYSK_MUSIC_REL")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { openInAppFolders() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text("Найти папку в приложении")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Исходное видео не меняется. Файлы всегда в $MAYSK_MUSIC_REL на внутренней памяти.",
                color = c.textDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}
