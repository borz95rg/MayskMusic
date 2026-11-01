package com.maysk.music.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maysk.music.ui.LocalActions
import com.maysk.music.ui.LocalStrings
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val color: Long,
    /** Резервные адреса потока: используются, если основной не отвечает. */
    val backupUrls: List<String> = emptyList(),
)

enum class RadioRegion(val color: Long) {
    CHECHEN(0xFF10B981),
    INGUSH(0xFF3B82F6),
    KAZAKH(0xFF0EA5E9),
    RUSSIAN(0xFFEC4899),
}

private fun regionTitle(r: RadioRegion, s: com.maysk.music.ui.Strings) = when (r) {
    RadioRegion.CHECHEN -> s.chechen
    RadioRegion.INGUSH -> s.ingush
    RadioRegion.KAZAKH -> s.kazakh
    RadioRegion.RUSSIAN -> s.russian
}

/** Запасные станции (если API недоступен). */
private val fallback: Map<RadioRegion, List<RadioStation>> = mapOf(
    RadioRegion.KAZAKH to listOf(
        RadioStation("qazaq", "Qazaq radiosy", "Қазақ · Folk", "https://radio-streams.kaztrk.kz/qazradio/qazradio/icecast.audio", 0xFF0EA5E9,
            backupUrls = listOf("https://qazaqradiosy.kz/live")),
        RadioStation("zhuldyz", "Жулдыз FM", "Қазақ · Pop", "https://online.zhuldyzfm.kz/zhuldyzfm", 0xFF6366F1,
            backupUrls = listOf("https://live.zhuldyzfm.kz/stream")),
        RadioStation("shalqar", "Shalqar", "Қазақ · Culture", "https://radio-streams.kaztrk.kz/shalqar/shalqar/icecast.audio", 0xFF0284C7),
        RadioStation("avto_kz", "Авторадио KZ", "Pop", "http://178.88.167.62:8080/AVTORADIO_192", 0xFFF59E0B),
        RadioStation("love_kz", "Love Radio KZ", "Hits", "https://stream.gakku.tv:8443/love128.mp3", 0xFFEC4899),
        RadioStation("vashe", "ВАШЕ РАДИО", "Pop", "https://live.zhanafm.kz:8443/vasheradio", 0xFF8B5CF6),
        RadioStation("beu", "beu FM", "Hits", "https://stream.beufm.kz/beufm", 0xFF14B8A6),
    ),
    RadioRegion.RUSSIAN to listOf(
        RadioStation("europa", "Europa Plus", "Pop", "https://ep128.hostingradio.ru:8030/ep128", 0xFFE91E63,
            backupUrls = listOf("https://ep256.hostingradio.ru:8052/ep256.mp3")),
        RadioStation("rusradio", "Русское Радио", "Pop", "https://icecast.russkoeradio.cdnvideo.ru/rr_h.mp3", 0xFFEF4444,
            backupUrls = listOf("https://pub0302.101.ru:8443/stream/air/aac/64/100")),
        RadioStation("dorognoe", "Дорожное Радио", "Pop · Road", "https://dorognoe.hostingradio.ru:8000/dorognoe", 0xFFF59E0B,
            backupUrls = listOf("https://icecast-dorognoe.cdnvideo.ru/dorognoe")),
        RadioStation("chanson", "Радио Шансон", "Chanson", "https://chanson.hostingradio.ru:8041/chanson128.mp3", 0xFF92400E),
        RadioStation("record", "Record", "Dance", "https://radiorecord.hostingradio.ru/rr_main96.aacp", 0xFF9C27B0),
        RadioStation("dfm", "DFM", "Club", "https://dfm.hostingradio.ru/dfm96.aacp", 0xFF2196F3),
        RadioStation("nashe", "Наше Радио", "Rock", "https://nashe1.hostingradio.ru/nashe-128.mp3", 0xFFE91E63),
        RadioStation("retro", "Ретро FM", "Retro", "https://retro.hostingradio.ru:8051/retro256.mp3", 0xFF795548),
        RadioStation("maximum", "Maximum", "Rock", "https://maximum.hostingradio.ru/maximum96.aacp", 0xFF607D8B),
        RadioStation("monte", "Monte Carlo", "Soft", "https://montecarlo.hostingradio.ru/montecarlo128.mp3", 0xFF3F51B5),
        RadioStation("energy", "Energy", "Dance", "https://pub0102.101.ru:8443/stream/trust/mp3/128/99", 0xFFFF5722),
    ),
    RadioRegion.CHECHEN to listOf(
        RadioStation("chechen_main", "Чеченское радио", "Нохчийн · Folk", "https://stream.radio05.ru/chechenskoe_radio_128", 0xFF10B981),
        RadioStation("groznyfm", "Грозный FM", "Нохчийн · Pop", "https://grozny.tv/live_radio.m3u8", 0xFF22C55E,
            backupUrls = listOf("http://online.groztrk.net:8000/vaynakh")),
        RadioStation("kavkaz", "Радио Кавказ", "Кавказ · Music", "https://stream.radio05.ru/radio_kavkaz_128", 0xFF059669),
        RadioStation("kavkaz_hit", "Кавказ Хит", "Hits · Кавказ", "https://kavkazhit.hostingradio.ru:8017/kavkazhit96.mp3", 0xFF34D399),
    ),
    RadioRegion.INGUSH to listOf(
        RadioStation("ingush_fm", "Радио Ингушетия", "Ингушетия", "http://77.87.97.62:8086/ingradio", 0xFF2563EB,
            backupUrls = listOf("http://stream.ingushetia.ru:8000/live")),
        RadioStation("kavkaz_ing", "Радио Кавказ", "Кавказ", "https://stream.radio05.ru/radio_kavkaz_128", 0xFF3B82F6),
        RadioStation("kavkaz_hit_ing", "Кавказ Хит", "Hits · Кавказ", "https://kavkazhit.hostingradio.ru:8017/kavkazhit96.mp3", 0xFF60A5FA),
    ),
)

private suspend fun probeUrl(url: String, timeoutMs: Int = 4000): Boolean = withContext(Dispatchers.IO) {
    try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MayskMusic/1.0")
            setRequestProperty("Icy-MetaData", "1")
        }
        val code = conn.responseCode
        conn.inputStream?.close()
        conn.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }
}

/** Проверяет все адреса станции; рабочий становится основным, остальные рабочие — резервными. */
private suspend fun resolveStation(st: RadioStation): RadioStation? = coroutineScope {
    val urls = (listOf(st.streamUrl) + st.backupUrls).distinct()
    val working = urls.map { u -> async { u to probeUrl(u) } }.awaitAll().filter { it.second }.map { it.first }
    if (working.isEmpty()) null else st.copy(streamUrl = working.first(), backupUrls = working.drop(1))
}

private suspend fun fetchFromRadioBrowser(query: String, limit: Int = 15): List<RadioStation> = withContext(Dispatchers.IO) {
    try {
        val q = URLEncoder.encode(query, "UTF-8")
        val api = "https://de1.api.radio-browser.info/json/stations/search?name=$q&hidebroken=true&limit=$limit&order=clickcount&reverse=true"
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "MayskMusic/1.0")
        }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(body)
        val colors = longArrayOf(0xFF0EA5E9, 0xFF10B981, 0xFF8B5CF6, 0xFFEC4899, 0xFFF59E0B, 0xFF3B82F6)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name")
            if (url.isBlank() || name.isBlank()) null
            else RadioStation(
                id = o.optString("stationuuid").ifBlank { name },
                name = name,
                genre = o.optString("tags").ifBlank { o.optString("codec") }.take(40),
                streamUrl = url,
                color = colors[i % colors.size],
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun loadRegionStations(region: RadioRegion): List<RadioStation> = coroutineScope {
    val keywords = when (region) {
        RadioRegion.CHECHEN -> listOf("чечен", "chechen", "вайнах", "vaynah", "нохч", "grozny", "грозн")
        RadioRegion.INGUSH -> listOf("ингуш", "ingush", "magas", "онгушт", "ongusht")
        RadioRegion.KAZAKH -> listOf("qazaq", "қазақ", "kazakh", "shalqar", "шалқар", "almaty", "astana", "казах")
        RadioRegion.RUSSIAN -> listOf("europa", "наше", "record", "dfm", "ретро", "maximum", "monte", "energy", "русск")
    }
    val queries = when (region) {
        RadioRegion.KAZAKH -> listOf("qazaq", "shalqar", "kazakhstan")
        RadioRegion.RUSSIAN -> listOf("europa plus", "nashe radio", "record radio")
        // Не ищем по caucasus/dagestan — туда попадают левые станции
        RadioRegion.CHECHEN -> listOf("чеченское радио", "chechen radio")
        RadioRegion.INGUSH -> listOf("ингушетия радио", "ingush radio")
    }
    val fromApi = queries.map { q -> async { fetchFromRadioBrowser(q, 12) } }.awaitAll().flatten()
        .distinctBy { it.streamUrl }
        .filter { st ->
            val n = st.name.lowercase()
            keywords.any { k -> n.contains(k.lowercase()) }
        }
        .take(15)

    val fallbackList = fallback[region] ?: emptyList()
    // Для Чечни/Ингушетии приоритет — проверенный curated-список
    val candidates = when (region) {
        RadioRegion.CHECHEN, RadioRegion.INGUSH -> (fallbackList + fromApi).distinctBy { it.streamUrl }
        else -> (fromApi + fallbackList).distinctBy { it.streamUrl }
    }
    val checked = candidates.map { st -> async { resolveStation(st) } }
        .awaitAll()
        .filterNotNull()

    if (checked.isNotEmpty()) return@coroutineScope checked
    // Даже если probe не прошёл (сеть/HEAD), для curated всё равно показываем — плеер сам попробует
    if (region == RadioRegion.CHECHEN || region == RadioRegion.INGUSH) {
        return@coroutineScope fallbackList
    }
    fallbackList
}

@Composable
fun RadioScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val str = LocalStrings.current
    var region by remember { mutableStateOf<RadioRegion?>(null) }
    var loading by remember { mutableStateOf(false) }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }
    val playerState by a.container.player.state.collectAsState()
    val playingTitle = playerState.title
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        a.container.player.onStreamMessage = { msg -> a.toast(msg) }
    }

    LaunchedEffect(region, refreshKey) {
        val r = region ?: return@LaunchedEffect
        loading = true
        stations = emptyList()
        stations = withContext(Dispatchers.IO) { loadRegionStations(r) }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = region?.let { regionTitle(it, str) } ?: str.radio,
            onBack = if (region != null) ({ region = null }) else null,
        ) {
            if (region != null) {
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Rounded.Refresh, str.refreshStations, tint = c.text)
                }
            }
        }

        if (region == null) {
            Text(
                str.radioRegionsHint,
                color = c.textDim,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(RadioRegion.entries.toList(), key = { it.name }) { r ->
                    val accent = Color(r.color)
                    GlassCard(
                        Modifier.fillMaxWidth().aspectRatio(1.05f).clickable { region = r; refreshKey++ },
                    ) {
                        Box(
                            Modifier.matchParentSize().background(
                                Brush.verticalGradient(listOf(accent.copy(alpha = 0.42f), Color.Transparent)),
                            ),
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Icon(Icons.Rounded.Radio, null, tint = accent, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(regionTitle(r, str), color = c.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            when {
                loading -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = c.accent)
                    Spacer(Modifier.height(12.dp))
                    Text(str.checkingStations, color = c.textDim)
                }
                stations.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(str.noWorkingStations, color = c.textDim)
                    Spacer(Modifier.height(12.dp))
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Rounded.Refresh, str.refreshStations, tint = c.accent)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "${str.workingStations}: ${stations.size}",
                            color = c.textDim,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(stations, key = { it.id }) { station ->
                        val isPlaying = playerState.isPlaying &&
                            playingTitle.contains(station.name, ignoreCase = true)
                        RadioCard(station, isPlaying) {
                            a.container.player.playStream(listOf(station.streamUrl) + station.backupUrls, station.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioCard(station: RadioStation, isPlaying: Boolean, onClick: () -> Unit) {
    val c = LocalMaysk.current
    val accent = Color(station.color)
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.5f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, color = c.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(station.genre, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (isPlaying) accent.copy(alpha = 0.25f) else c.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = if (isPlaying) accent else c.accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
