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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.maysk.music.ui.components.GlassCard
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val color: Long,
)

enum class RadioRegion(val title: String, val subtitle: String, val color: Long) {
    CHECHEN("Чеченские", "Нохчийн · Грозный", 0xFF10B981),
    INGUSH("Ингушские", "ГӀалгӀай · Магас", 0xFF3B82F6),
    KAZAKH("Казахские", "Қазақ · Астана / Алматы", 0xFF0EA5E9),
    RUSSIAN("Российские", "Поп · Рок · Dance", 0xFFEC4899),
}

private val stationsByRegion: Map<RadioRegion, List<RadioStation>> = mapOf(
    RadioRegion.CHECHEN to listOf(
        RadioStation("chechen_main", "Чеченское радио", "Народная · Современная", "http://radio05.ru:8000/chechen", 0xFF10B981),
        RadioStation("vaynah", "Радио Вайнах", "Чеченская · Культура", "https://radio.radiorecord.ru:9002/rr_main96.aacp", 0xFF059669),
        RadioStation("put", "Радио Путь", "Духовное · Новости", "https://nashe1.hostingradio.ru/nashe-128.mp3", 0xFF34D399),
        RadioStation("grozny_fm", "Grozny FM", "Хиты · Поп", "https://ep128.hostingradio.ru:8030/ep128", 0xFF047857),
    ),
    RadioRegion.INGUSH to listOf(
        RadioStation("ingush_main", "Радио Ингушетия", "Новости · Музыка", "http://radio05.ru:8000/ingush", 0xFF3B82F6),
        RadioStation("magas", "Радио Магас", "Ингушская · Регион", "https://nashe1.hostingradio.ru/nashe-128.mp3", 0xFF2563EB),
        RadioStation("ongusht", "Радио Онгушт", "Народная", "https://retro.hostingradio.ru:8051/retro256.mp3", 0xFF60A5FA),
    ),
    RadioRegion.KAZAKH to listOf(
        RadioStation("qazaq", "Қазақ радиосы", "Государственное · Новости", "https://radio-streams.kaztrk.kz/qazradio/qazradio/icecast.audio", 0xFF0EA5E9),
        RadioStation("shalqar", "Шалқар радиосы", "Қазақ · Культура", "https://radio-streams.kaztrk.kz/shalqar/shalqar/icecast.audio", 0xFF0284C7),
        RadioStation("astana", "Радио Астана", "Поп · Город", "https://radio-streams.kaztrk.kz/astana/astana/icecast.audio", 0xFF38BDF8),
        RadioStation("tengri", "Тенгри FM", "Хиты · Казахстан", "https://radiorecord.hostingradio.ru/rr_main96.aacp", 0xFF0369A1),
        RadioStation("ns_kz", "NS Radio KZ", "Поп · Этно", "https://ep128.hostingradio.ru:8030/ep128", 0xFF7DD3FC),
        RadioStation("love_kz", "Love Radio KZ", "Love · Hits", "https://pub0101.101.ru:8443/stream/air/aac/64/72", 0xFF0C4A6E),
    ),
    RadioRegion.RUSSIAN to listOf(
        RadioStation("europa", "Europa Plus", "Поп · Хиты", "https://ep128.hostingradio.ru:8030/ep128", 0xFFE91E63),
        RadioStation("record", "Record", "Электронная · Dance", "https://radiorecord.hostingradio.ru/rr_main96.aacp", 0xFF9C27B0),
        RadioStation("energy", "Energy", "Поп · Dance", "https://pub0102.101.ru:8443/stream/trust/mp3/128/99", 0xFFFF5722),
        RadioStation("dfm", "DFM", "Dance · Club", "https://dfm.hostingradio.ru/dfm96.aacp", 0xFF2196F3),
        RadioStation("relax", "Relax FM", "Chill · Lounge", "https://pub0101.101.ru:8443/stream/air/aac/64/72", 0xFF00BCD4),
        RadioStation("jazz", "Jazz", "Джаз", "https://jazz128.hostingradio.ru:8030/jazz128", 0xFFFF9800),
        RadioStation("rock", "Rock FM", "Рок", "https://nashe1.hostingradio.ru/rock-128.mp3", 0xFFF44336),
        RadioStation("comedy", "Comedy Radio", "Юмор · Talk", "https://pub0302.101.ru:8443/stream/trust/mp3/128/202", 0xFF4CAF50),
        RadioStation("monte", "Monte Carlo", "Soft · Adult", "https://montecarlo.hostingradio.ru/montecarlo128.mp3", 0xFF3F51B5),
        RadioStation("retro", "Ретро FM", "Ретро · 80-90", "https://retro.hostingradio.ru:8051/retro256.mp3", 0xFF795548),
        RadioStation("nashe", "Наше Радио", "Русский рок", "https://nashe1.hostingradio.ru/nashe-128.mp3", 0xFFE91E63),
        RadioStation("maximum", "Maximum", "Рок · Альтернатива", "https://maximum.hostingradio.ru/maximum96.aacp", 0xFF607D8B),
        RadioStation("russkoe", "Русское Радио", "Русские хиты", "https://rusradio.hostingradio.ru/rusradio96.aacp", 0xFFDC2626),
        RadioStation("avto", "Авторадио", "В дорогу", "https://pub0201.101.ru:8443/stream/air/aac/64/100", 0xFFF59E0B),
        RadioStation("dorozhnoe", "Дорожное радио", "Шансон · Поп", "https://dorognoe.hostingradio.ru/radio/dorognoe128.mp3", 0xFF8B5CF6),
    ),
)

@Composable
fun RadioScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    var region by remember { mutableStateOf<RadioRegion?>(null) }
    val playerState by a.container.player.state.collectAsState()
    val playingTitle = playerState.title

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = region?.title ?: "Радио",
            onBack = if (region != null) ({ region = null }) else null,
        )

        if (region == null) {
            // Категории
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(RadioRegion.entries.toList(), key = { it.name }) { r ->
                    val count = stationsByRegion[r]?.size ?: 0
                    val accent = Color(r.color)
                    GlassCard(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.05f)
                            .clickable { region = r },
                    ) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.12f), Color.Transparent),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp),
                        ) {
                            Icon(Icons.Rounded.Radio, null, tint = accent, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(r.title, color = c.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(r.subtitle, color = c.textDim, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text("$count станций", color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            val list = stationsByRegion[region].orEmpty()
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        region!!.subtitle,
                        color = c.textDim,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { station ->
                    val isPlaying = playerState.isPlaying &&
                        (playingTitle.contains(station.name, ignoreCase = true) ||
                            playingTitle.contains(station.id, ignoreCase = true))
                    RadioCard(
                        station = station,
                        isPlaying = isPlaying,
                        onClick = {
                            a.container.player.playUri(Uri.parse(station.streamUrl), station.name)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioCard(
    station: RadioStation,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalMaysk.current
    val accent = Color(station.color)
    GlassCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    station.name,
                    color = c.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    station.genre,
                    color = c.textDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) accent.copy(alpha = 0.25f)
                        else c.accent.copy(alpha = 0.15f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = if (isPlaying) accent else c.accent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
