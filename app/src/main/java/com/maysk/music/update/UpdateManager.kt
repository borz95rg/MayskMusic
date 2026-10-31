package com.maysk.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.maysk.music.BuildConfig
import com.maysk.music.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionName: String, val build: Int, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * Обновления через GitHub Releases: GitHub Actions публикует APK с тегом v1.0.N,
 * приложение сравнивает N со своим versionCode, скачивает APK и открывает установщик.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val prefs = context.getSharedPreferences("maysk_update", Context.MODE_PRIVATE)

    val repo: String
        get() = settings.current.customUpdateRepo.trim().ifEmpty { BuildConfig.UPDATE_REPO }

    val currentBuild: Int get() = BuildConfig.VERSION_CODE
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Автопроверка не чаще раза в 6 часов. */
    fun autoCheck() {
        if (!settings.current.autoUpdateCheck || repo.isBlank()) return
        val last = prefs.getLong("last_check", 0)
        if (System.currentTimeMillis() - last < 6 * 3600_000L) {
            // Покажем ранее найденное обновление, если оно всё ещё новее.
            return
        }
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (repo.isBlank()) {
            _status.value = UpdateStatus.Error("Не указан репозиторий GitHub для обновлений")
            return
        }
        if (_status.value is UpdateStatus.Downloading) return
        if (!silent) _status.value = UpdateStatus.Checking
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            result.onSuccess { info ->
                _status.value = when {
                    info != null && info.build > currentBuild -> UpdateStatus.Available(info)
                    silent -> UpdateStatus.Idle
                    else -> UpdateStatus.UpToDate
                }
            }.onFailure {
                _status.value = if (silent) UpdateStatus.Idle else UpdateStatus.Error("Не удалось проверить обновления: ${it.message ?: "нет сети"}")
            }
        }
    }

    fun dismiss() { _status.value = UpdateStatus.Idle }

    private fun fetchLatest(): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "MayskMusic/${currentVersion}")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode == 404) throw IllegalStateException("релизы не найдены (репозиторий приватный или ещё нет сборок)")
            if (conn.responseCode !in 200..299) throw IllegalStateException("GitHub ответил ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val build = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk")) {
                    return UpdateInfo(
                        versionName = tag.removePrefix("v"),
                        build = build,
                        notes = json.optString("body").take(1500),
                        apkUrl = a.getString("browser_download_url"),
                        sizeBytes = a.optLong("size"),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    fun download(info: UpdateInfo) {
        if (_status.value is UpdateStatus.Downloading) return
        _status.value = UpdateStatus.Downloading(info, 0f)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val out = File(dir, "MayskMusic-${info.versionName}.apk")
                    fun open(u: URL): HttpURLConnection = (u.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "MayskMusic/${currentVersion}")
                        connectTimeout = 20_000
                        readTimeout = 30_000
                    }
                    var url = URL(info.apkUrl)
                    var conn = open(url)
                    var redirects = 0
                    while (conn.responseCode in 300..399 && redirects < 5) {
                        val loc = conn.getHeaderField("Location") ?: break
                        conn.disconnect()
                        url = URL(url, loc)
                        conn = open(url)
                        redirects++
                    }
                    if (conn.responseCode !in 200..299) throw IllegalStateException("код ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0 && done - lastEmit > 256 * 1024) {
                                    lastEmit = done
                                    val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) { _status.value = UpdateStatus.Downloading(info, p) }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    out
                }
            }
            result.onSuccess { file ->
                _status.value = UpdateStatus.ReadyToInstall(info, file)
                install(file)
            }.onFailure {
                _status.value = UpdateStatus.Error("Ошибка загрузки: ${it.message ?: "нет сети"}")
            }
        }
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(file: File) {
        if (!canInstallPackages()) {
            openInstallPermissionSettings()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _status.value = UpdateStatus.Error("Не удалось открыть установщик") }
    }
}
