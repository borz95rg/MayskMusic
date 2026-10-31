package com.maysk.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maysk.music.ui.AppRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as MayskApp).container
    val hasPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasPermission.value = audioPermissionGranted()
        container.player.connect()
        if (hasPermission.value) container.library.start()
        container.updates.autoCheck()

        setContent {
            AppRoot(
                container = container,
                hasPermission = hasPermission.value,
                onPermissionResult = { granted ->
                    hasPermission.value = granted || audioPermissionGranted()
                    if (hasPermission.value) container.library.start()
                },
            )
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Пользователь мог выдать разрешение в настройках системы.
        if (!hasPermission.value && audioPermissionGranted()) {
            hasPermission.value = true
            container.library.start()
        }
    }

    override fun onStop() {
        super.onStop()
        container.saveQueueNow()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            container.player.playUri(uri, displayName(uri))
        } else if (intent.getBooleanExtra(EXTRA_AUTOPLAY, false)) {
            lifecycleScope.launch {
                delay(1200)
                container.player.togglePlay()
            }
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        } ?: uri.lastPathSegment ?: "Аудиофайл"
    } catch (e: Exception) { uri.lastPathSegment ?: "Аудиофайл" }

    private fun audioPermissionGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_AUTOPLAY = "autoplay"
    }
}
