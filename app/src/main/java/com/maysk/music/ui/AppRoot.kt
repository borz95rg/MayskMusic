package com.maysk.music.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.maysk.music.AppContainer
import com.maysk.music.data.MiniPlayerStyle
import com.maysk.music.data.ThemePreset
import com.maysk.music.data.FileOps
import com.maysk.music.data.Track
import com.maysk.music.ui.components.AddToPlaylistDialog
import com.maysk.music.ui.components.BatchTagDialog
import com.maysk.music.ui.components.MoveCopyDialog
import com.maysk.music.ui.components.TagEditorDialog
import com.maysk.music.ui.components.AppBackground
import com.maysk.music.ui.components.Artwork
import com.maysk.music.ui.components.ConfirmDialog
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.GradientButton
import com.maysk.music.ui.components.TrackMenuSheet
import com.maysk.music.ui.screens.AiScreen
import com.maysk.music.ui.screens.AlbumScreen
import com.maysk.music.ui.screens.MoodScreen
import com.maysk.music.ui.screens.SimilarScreen
import com.maysk.music.ui.screens.AppearanceScreen
import com.maysk.music.ui.screens.ArtistScreen
import com.maysk.music.ui.screens.DuplicatesScreen
import com.maysk.music.ui.screens.EqualizerScreen
import com.maysk.music.ui.screens.FolderManagerScreen
import com.maysk.music.ui.screens.FolderScreen
import com.maysk.music.ui.screens.FoldersScreen
import com.maysk.music.ui.screens.GenreScreen
import com.maysk.music.ui.screens.HomeScreen
import com.maysk.music.ui.screens.MixScreen
import com.maysk.music.ui.screens.MyMusicScreen
import com.maysk.music.ui.screens.PlayerScreen
import com.maysk.music.ui.screens.PlaylistScreen
import com.maysk.music.ui.screens.PlaylistsScreen
import com.maysk.music.ui.screens.SearchScreen
import com.maysk.music.ui.screens.SettingsScreen
import com.maysk.music.ui.screens.StatsScreen
import com.maysk.music.ui.screens.TracksScreen
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.MayskTheme
import com.maysk.music.ui.theme.buildColors
import com.maysk.music.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppRoot(container: AppContainer, hasPermission: Boolean, onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val settings by container.settings.state.collectAsState()
    val playback by container.player.state.collectAsState()
    val systemDark = isSystemInDarkTheme()

    var dynamicAccent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(playback.artUri, settings.theme) {
        if (settings.theme == ThemePreset.DYNAMIC) {
            dynamicAccent = withContext(Dispatchers.IO) { extractAccent(context, playback.artUri) }
        }
    }
    val colors = remember(settings, dynamicAccent, systemDark) { buildColors(settings, dynamicAccent, systemDark) }

    MayskTheme(settings, colors) {
        AppBackground {
            if (!hasPermission) PermissionScreen(onPermissionResult)
            else MainScaffold(container)
        }
    }
}

@Composable
private fun MainScaffold(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember { Nav() }
    val snackbar = remember { SnackbarHostState() }
    val playback by container.player.state.collectAsState()
    val settings by container.settings.state.collectAsState()

    var showPlayer by remember { mutableStateOf(false) }
    var menuTracks by remember { mutableStateOf<List<Track>?>(null) }
    var playlistTracks by remember { mutableStateOf<List<Track>?>(null) }
    var deleteTracks by remember { mutableStateOf<List<Track>?>(null) }
    var imageCallback by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    var tagTracks by remember { mutableStateOf<List<Track>?>(null) }
    var moveTracks by remember { mutableStateOf<Pair<List<Track>, Boolean>?>(null) }
    val toastRef: (String) -> Unit = remember {
        { msg: String -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }; Unit }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            container.library.rescan(debounceMs = 300)
            scope.launch { snackbar.showSnackbar("Файлы удалены") }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) imageCallback?.invoke(uri)
        imageCallback = null
    }

    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pendingWrite
        pendingWrite = null
        if (res.resultCode == Activity.RESULT_OK) action?.invoke() else toastRef("Изменение файлов не разрешено")
    }
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val action = pendingWrite
        pendingWrite = null
        if (ok) action?.invoke() else toastRef("Нужно разрешение на запись в память")
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            toastRef("«$text» → ${VoiceCommands.handle(text, container)}")
        }
    }

    fun requestWrite(tracks: List<Track>, onGranted: () -> Unit) {
        val cr = context.contentResolver
        when {
            tracks.isEmpty() -> onGranted()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> try {
                pendingWrite = onGranted
                val pi = MediaStore.createWriteRequest(cr, tracks.map { it.uri })
                writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } catch (e: Exception) {
                pendingWrite = null
                toastRef("Не удалось запросить доступ к файлам")
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                // Android 10 спрашивает по одному файлу: проверяем и запрашиваем по очереди.
                var request: android.content.IntentSender? = null
                for (t in tracks) {
                    try {
                        cr.openFileDescriptor(t.uri, "rw")?.close()
                    } catch (e: SecurityException) {
                        if (e is RecoverableSecurityException) { request = e.userAction.actionIntent.intentSender; break }
                    } catch (_: Exception) { }
                }
                val sender = request
                if (sender == null) onGranted()
                else {
                    pendingWrite = { requestWrite(tracks, onGranted) }
                    writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> onGranted()
            else -> {
                pendingWrite = onGranted
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    val actions = remember {
        UiActions(
            container = container,
            nav = nav,
            openPlayer = { showPlayer = true },
            openMenu = { menuTracks = it },
            addToPlaylist = { playlistTracks = it },
            delete = { deleteTracks = it },
            pickImage = { cb ->
                imageCallback = cb
                imageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            toast = toastRef,
            requestWrite = { tracks, onGranted -> requestWrite(tracks, onGranted) },
            fileJob = { tracks, needsWrite, success, op ->
                val run: () -> Unit = {
                    container.scope.launch {
                        toastRef(if (tracks.size == 1) "Обработка…" else "Обработка ${tracks.size} файлов…")
                        val results = withContext(Dispatchers.IO) { tracks.map { op(it) } }
                        val scan = results.flatMap { it.scan }
                        withContext(Dispatchers.IO) { runCatching { FileOps.rescanFiles(context, scan) } }
                        container.library.rescan(debounceMs = 400)
                        val errors = results.mapNotNull { it.error }
                        toastRef(
                            when {
                                errors.isEmpty() -> success
                                errors.size == results.size -> errors.first()
                                else -> "$success. Ошибок: ${errors.size} (${errors.first()})"
                            }
                        )
                    }
                }
                if (needsWrite) requestWrite(tracks, run) else run()
            },
            editTags = { tagTracks = it },
            moveCopy = { tracks, copy -> moveTracks = tracks to copy },
            voice = {
                val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Например: «включи Imagine Dragons», «пауза», «таймер 30 минут»")
                try { voiceLauncher.launch(intent) } catch (e: Exception) { toastRef("Распознавание речи недоступно на этом устройстве") }
            },
        )
    }

    BackHandler(enabled = nav.stack.size > 1 || nav.tab != 0) { nav.pop() }
    BackHandler(enabled = showPlayer) { showPlayer = false }

    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    val animMs = if (settings.animationsEnabled) (220 / settings.animSpeed).toInt() else 0
                    AnimatedContent(
                        targetState = nav.current,
                        transitionSpec = { fadeIn(tween(animMs)) togetherWith fadeOut(tween(animMs)) },
                        label = "route",
                    ) { route -> RouteContent(route) }
                }
                if (playback.hasMedia) MiniPlayer(onOpen = { showPlayer = true })
                BottomNav(nav)
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically(tween(if (settings.animationsEnabled) 320 else 0)) { it } + fadeIn(),
                exit = slideOutVertically(tween(if (settings.animationsEnabled) 280 else 0)) { it } + fadeOut(),
            ) {
                PlayerScreen(onClose = { showPlayer = false })
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 140.dp))
        }

        menuTracks?.let { TrackMenuSheet(it) { menuTracks = null } }
        playlistTracks?.let { AddToPlaylistDialog(it) { playlistTracks = null } }
        deleteTracks?.let { list ->
            ConfirmDialog(
                title = "Удалить с устройства?",
                text = if (list.size == 1) "Файл «${list.first().title}» будет удалён из памяти телефона. Это действие нельзя отменить."
                else "Будет удалено файлов: ${list.size}. Это действие нельзя отменить.",
                onDismiss = { deleteTracks = null },
                onConfirm = {
                    deleteTracks = null
                    performDelete(context, list, onIntentSender = { sender ->
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }, onDone = { ok ->
                        if (ok) container.library.rescan(debounceMs = 300)
                        actions.toast(if (ok) "Файлы удалены" else "Не удалось удалить файлы")
                    })
                },
            )
        }
        tagTracks?.let { list ->
            if (list.size == 1) TagEditorDialog(list.first()) { tagTracks = null }
            else BatchTagDialog(list) { tagTracks = null }
        }
        moveTracks?.let { (list, copy) -> MoveCopyDialog(list, copy) { moveTracks = null } }
        UpdateDialogs(container)
    }
}

private fun performDelete(
    context: Context,
    tracks: List<Track>,
    onIntentSender: (android.content.IntentSender) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val cr = context.contentResolver
    val uris = tracks.map { it.uri }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            onIntentSender(MediaStore.createDeleteRequest(cr, uris).intentSender)
        } catch (e: Exception) { onDone(false) }
        return
    }
    try {
        uris.forEach { cr.delete(it, null, null) }
        onDone(true)
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            onIntentSender(e.userAction.actionIntent.intentSender)
        } else onDone(false)
    } catch (e: Exception) {
        onDone(false)
    }
}

@Composable
private fun RouteContent(route: Route) {
    when (route) {
        Route.Home -> HomeScreen()
        Route.Tracks -> TracksScreen(initialTab = 0)
        is Route.LibraryTab -> TracksScreen(initialTab = route.tab, showBack = true)
        Route.Folders -> FoldersScreen()
        Route.Playlists -> PlaylistsScreen()
        Route.MyMusic -> MyMusicScreen()
        is Route.AlbumR -> AlbumScreen(route.id)
        is Route.ArtistR -> ArtistScreen(route.name)
        is Route.GenreR -> GenreScreen(route.name)
        is Route.FolderR -> FolderScreen(route.path)
        is Route.PlaylistR -> PlaylistScreen(route.id)
        is Route.MixR -> MixScreen(route.kind)
        Route.Search -> SearchScreen()
        Route.Settings -> SettingsScreen()
        Route.Appearance -> AppearanceScreen()
        Route.Equalizer -> EqualizerScreen()
        Route.Stats -> StatsScreen()
        Route.Duplicates -> DuplicatesScreen()
        Route.FolderManager -> FolderManagerScreen()
        Route.Ai -> AiScreen()
        is Route.Similar -> SimilarScreen(route.trackId)
        is Route.Mood -> MoodScreen(route.kind)
    }
}

@Composable
private fun BottomNav(nav: Nav) {
    val c = LocalMaysk.current
    val items = listOf(
        Triple("Главная", Icons.Rounded.Home, 0),
        Triple("Треки", Icons.Rounded.MusicNote, 1),
        Triple("Папки", Icons.Rounded.Folder, 2),
        Triple("Плейлисты", Icons.Rounded.QueueMusic, 3),
        Triple("Моя музыка", Icons.Rounded.Person, 4),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (c.isLight) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, icon, i) ->
            NavItem(label, icon, nav.tab == i, Modifier.weight(1f)) { nav.selectTab(i) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalMaysk.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (selected) c.accent else c.textDim, modifier = Modifier.size(24.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) c.accent else c.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val actions = LocalActions.current
    val c = LocalMaysk.current
    val settings = com.maysk.music.ui.theme.LocalUi.current
    val state by actions.container.player.state.collectAsState()
    val pos by actions.container.player.position.collectAsState()
    val progress = if (state.durationMs > 0) (pos.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val compact = settings.miniPlayer == MiniPlayerStyle.COMPACT
    val content: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(state.artUri, Modifier.size(if (compact) 40.dp else 46.dp), corner = 10.dp, seed = state.currentId ?: 0L)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(state.artist, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { actions.container.player.togglePlay() }) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Пауза" else "Играть", tint = c.text, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { actions.container.player.next() }) {
                    Icon(Icons.Rounded.SkipNext, "Следующий", tint = c.text)
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(c.text.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Brush.horizontalGradient(listOf(c.accent, c.accent3))))
            }
        }
    }
    if (compact) {
        Box(Modifier.fillMaxWidth().background(if (c.isLight) Color.White.copy(alpha = 0.9f) else Color(0xFF0E0B1E).copy(alpha = 0.92f))) { content() }
    } else {
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), corner = 18.dp, highlight = false) { content() }
    }
}

@Composable
private fun PermissionScreen(onResult: (Boolean) -> Unit) {
    val c = LocalMaysk.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val audio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        onResult(res[audio] == true)
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(c.accent2, c.accent, c.accent3))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.LibraryMusic, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(28.dp))
        Text("Maysk Music", style = MaterialTheme.typography.headlineLarge, color = c.text)
        Text("Твоя музыка. Твой стиль. Твои правила.", color = c.textDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Text(
            "Чтобы показать вашу музыку, приложению нужен доступ к аудиофайлам на устройстве. " +
                "Интернет и регистрация для прослушивания не нужны.",
            color = c.text, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("Разрешить доступ к музыке", Modifier.fillMaxWidth()) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            launcher.launch(perms)
        }
    }
}

@Composable
private fun UpdateDialogs(container: AppContainer) {
    val status by container.updates.status.collectAsState()
    val actions = LocalActions.current
    when (val s = status) {
        is UpdateStatus.Available -> AlertDialog(
            onDismissRequest = { container.updates.dismiss() },
            title = { Text("Доступно обновление ${s.info.versionName}") },
            text = {
                Column {
                    Text("Установлена версия ${container.updates.currentVersion}.")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Что нового:", fontWeight = FontWeight.SemiBold)
                        Text(s.info.notes, maxLines = 10, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.info.sizeBytes > 0) Text("Размер: %.1f МБ".format(s.info.sizeBytes / 1_048_576.0), color = LocalMaysk.current.textDim)
                }
            },
            confirmButton = { TextButton(onClick = { container.updates.download(s.info) }) { Text("Обновить") } },
            dismissButton = { TextButton(onClick = { container.updates.dismiss() }) { Text("Позже") } },
        )
        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка обновления") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )
        is UpdateStatus.ReadyToInstall -> AlertDialog(
            onDismissRequest = { container.updates.dismiss() },
            title = { Text("Обновление загружено") },
            text = {
                Text(
                    if (container.updates.canInstallPackages()) "Нажмите «Установить», чтобы обновить приложение до ${s.info.versionName}."
                    else "Разрешите установку из этого источника в открывшихся настройках, затем вернитесь и нажмите «Установить»."
                )
            },
            confirmButton = { TextButton(onClick = { container.updates.install(s.file) }) { Text("Установить") } },
            dismissButton = { TextButton(onClick = { container.updates.dismiss() }) { Text("Позже") } },
        )
        is UpdateStatus.UpToDate -> LaunchedEffect(s) {
            actions.toast("У вас последняя версия ${container.updates.currentVersion}")
            container.updates.dismiss()
        }
        is UpdateStatus.Error -> LaunchedEffect(s) {
            actions.toast(s.message)
            container.updates.dismiss()
        }
        else -> {}
    }
}

private fun extractAccent(context: Context, uri: Uri?): Color? {
    uri ?: return null
    return try {
        val bmp = context.contentResolver.openInputStream(uri)?.use { s ->
            BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = 8 })
        } ?: return null
        val p = Palette.from(bmp).generate()
        val rgb = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return null
        Color(rgb)
    } catch (e: Exception) { null }
}
