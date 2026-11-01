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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
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
import com.maysk.music.ui.components.SectionHeader
import com.maysk.music.ui.components.TopBar
import com.maysk.music.ui.theme.LocalMaysk

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val color: Long,
)

private val stations = listOf(
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
)

@Composable
fun RadioScreen() {
    val a = LocalActions.current
    val c = LocalMaysk.current
    val playerState by a.container.player.state.collectAsState()
    val currentId = playerState.currentId?.toString() ?: playerState.title

    Column(Modifier.fillMaxSize()) {
        TopBar("Радио")
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Онлайн-станции · нажмите, чтобы слушать",
                    color = c.textDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(stations, key = { it.id }) { station ->
                val isPlaying = playerState.isPlaying && (
                    currentId.contains(station.id, ignoreCase = true) ||
                        playerState.title.contains(station.name, ignoreCase = true)
                    )
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
                            listOf(accent.copy(alpha = 0.9f), accent.copy(alpha = 0.55f)),
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
