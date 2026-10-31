package com.maysk.music.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maysk.music.AppContainer
import com.maysk.music.BuildConfig
import com.maysk.music.data.Track
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.Route
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.SleepTimerDialog
import com.maysk.music.ui.components.TextInputDialog
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.update.UpdateStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val context = LocalContext.current
    val settings by a.container.settings.state.collectAsState()
    val updStatus by a.container.updates.status.collectAsState()
    var timer by remember { mutableStateOf(false) }
    var editRepo by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) a.toast(if (Backup.export(context, uri, a.container)) "Резервная копия сохранена" else "Не удалось сохранить")
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) a.toast(Backup.import(context, uri, a.container))
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Настройки", onBack = { a.nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Group("Библиотека") {
                Item(Icons.Rounded.FolderOpen, "Музыкальные папки", "Выбор и исключение папок") { a.nav.push(Route.FolderManager) }
                Item(Icons.Rounded.Refresh, "Обновить библиотеку", "Пересканировать музыку на устройстве") {
                    a.container.library.rescan(); a.toast("Сканирование…")
                }
            }
            Group("Звук") {
                Item(Icons.Rounded.Equalizer, "Эквалайзер и эффекты", null) { a.nav.push(Route.Equalizer) }
                Item(Icons.Rounded.Timer, "Таймер сна", null) { timer = true }
            }
            Group("Интерфейс") {
                Item(Icons.Rounded.Palette, "Оформление", "Темы, цвета, плеер, шрифты") { a.nav.push(Route.Appearance) }
                ToggleItem(Icons.Rounded.Accessibility, "Простой режим", "Крупные кнопки и текст", settings.simpleMode) { v ->
                    a.container.settings.update { it.copy(simpleMode = v) }
                }
                ToggleItem(Icons.Rounded.Visibility, "Высокий контраст", null, settings.highContrast) { v ->
                    a.container.settings.update { it.copy(highContrast = v) }
                }
                ToggleItem(Icons.Rounded.Refresh, "Анимации", "Отключите для экономии батареи", settings.animationsEnabled) { v ->
                    a.container.settings.update { it.copy(animSpeed = if (v) 1f else 0f) }
                }
            }
            Group("Резервная копия") {
                Item(Icons.Rounded.Backup, "Сохранить копию", "Плейлисты, любимое, тексты, эквалайзер, настройки") {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exporter.launch("maysk-music-backup-$date.json")
                }
                Item(Icons.Rounded.Restore, "Восстановить из копии", "Треки сопоставляются по пути к файлу") {
                    importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            Group("Обновления") {
                val repo = a.container.updates.repo
                Item(
                    Icons.Rounded.SystemUpdate,
                    when (updStatus) {
                        is UpdateStatus.Checking -> "Проверка…"
                        is UpdateStatus.Downloading -> "Загрузка обновления…"
                        else -> "Проверить обновления"
                    },
                    "Версия ${BuildConfig.VERSION_NAME}" + if (repo.isBlank()) " · источник не задан" else " · $repo",
                ) {
                    if (repo.isBlank()) editRepo = true else a.container.updates.check()
                }
                ToggleItem(Icons.Rounded.Refresh, "Проверять автоматически", "При запуске, не чаще раза в 6 часов", settings.autoUpdateCheck) { v ->
                    a.container.settings.update { it.copy(autoUpdateCheck = v) }
                }
                Item(Icons.Rounded.Info, "Источник обновлений", "GitHub-репозиторий: ${repo.ifBlank { "не задан" }}") { editRepo = true }
            }
            Group("О приложении") {
                Column(Modifier.padding(16.dp)) {
                    Text("Maysk Music ${BuildConfig.VERSION_NAME}", color = c.text, style = MaterialTheme.typography.titleMedium)
                    Text("Твоя музыка. Твой стиль. Твои правила.", color = c.textDim)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Работает без интернета и регистрации. Интернет используется только для проверки обновлений.",
                        color = c.textDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (timer) SleepTimerDialog { timer = false }
    if (editRepo) {
        TextInputDialog(
            title = "Репозиторий обновлений",
            initial = settings.customUpdateRepo.ifBlank { BuildConfig.UPDATE_REPO },
            label = "владелец/репозиторий",
            onDismiss = { editRepo = false },
        ) { v ->
            val cleaned = v.trim().removePrefix("https://github.com/").trimEnd('/')
            a.container.settings.update { it.copy(customUpdateRepo = if (cleaned == BuildConfig.UPDATE_REPO) "" else cleaned) }
            editRepo = false
            if (cleaned.isNotBlank()) a.container.updates.check()
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = LocalMaysk.current
    Text(title, color = c.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp))
    GlassCard(Modifier.fillMaxWidth()) { Column { content() } }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text)
            if (subtitle != null) Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToggleItem(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text)
            if (subtitle != null) Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

/** Резервное копирование в JSON. ID треков дополнительно сохраняются как пути, чтобы восстановить на другом телефоне. */
object Backup {
    fun export(context: Context, uri: Uri, c: AppContainer): Boolean = try {
        val lib = c.library.library.value
        val data = c.userData.toJson()
        data.remove("queue")
        val paths = JSONObject()
        val ids = mutableSetOf<Long>()
        c.userData.playlists.value.forEach { ids += it.trackIds }
        ids += c.userData.favorites.value
        ids += c.userData.lyrics.value.keys
        c.userData.history.value.forEach { ids += it.trackId }
        ids.forEach { id -> lib.byId[id]?.let { paths.put(id.toString(), it.path) } }
        val root = JSONObject()
            .put("app", "maysk-music")
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("userData", data)
            .put("paths", paths)
            .put("settings", c.settings.exportJson())
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
        true
    } catch (e: Exception) { false }

    fun import(context: Context, uri: Uri, c: AppContainer): String = try {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        val root = JSONObject(text)
        if (root.optString("app") != "maysk-music") "Это не резервная копия Maysk Music"
        else {
            val lib = c.library.library.value
            val byPath: Map<String, Track> = lib.tracks.associateBy { it.path }
            val paths = root.optJSONObject("paths") ?: JSONObject()
            // старый id -> новый id (по пути к файлу)
            val remap = HashMap<Long, Long>()
            paths.keys().forEach { k -> byPath[paths.getString(k)]?.let { remap[k.toLong()] = it.id } }
            fun map(id: Long): Long = remap[id] ?: id
            val data = root.getJSONObject("userData")
            data.optJSONArray("playlists")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val t = p.optJSONArray("tracks") ?: continue
                    val n = JSONArray()
                    for (j in 0 until t.length()) n.put(map(t.getLong(j)))
                    p.put("tracks", n)
                }
            }
            data.optJSONArray("favorites")?.let { f ->
                val n = JSONArray(); for (j in 0 until f.length()) n.put(map(f.getLong(j))); data.put("favorites", n)
            }
            data.optJSONArray("history")?.let { h ->
                for (j in 0 until h.length()) { val e = h.getJSONArray(j); e.put(0, map(e.getLong(0))) }
            }
            data.optJSONObject("lyrics")?.let { l ->
                val n = JSONObject(); l.keys().forEach { k -> n.put(map(k.toLong()).toString(), l.getString(k)) }; data.put("lyrics", n)
            }
            c.userData.importJson(data)
            root.optJSONObject("settings")?.let { c.settings.importJson(it) }
            c.library.applyFilters()
            "Данные восстановлены"
        }
    } catch (e: Exception) { "Не удалось прочитать резервную копию" }
}
