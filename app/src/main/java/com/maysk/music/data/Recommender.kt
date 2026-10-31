package com.maysk.music.data

import java.util.Calendar
import kotlin.math.abs

enum class MoodKind(val title: String, val subtitle: String, val keywords: List<String>) {
    ENERGY("Энергия", "Рок, электроника, рэп", listOf("rock", "metal", "electro", "edm", "techno", "drum", "dubstep", "trap", "hip", "rap", "punk", "phonk", "рок", "рэп")),
    PARTY("Вечеринка", "Танцевальное и поп", listOf("dance", "pop", "house", "disco", "club", "reggaeton", "electro", "funk", "k-pop", "поп", "танц")),
    FOCUS("Фокус", "Инструментальная и эмбиент", listOf("instrumental", "classical", "ambient", "lo-fi", "lofi", "piano", "soundtrack", "post-rock", "chillout", "score", "классика")),
    CALM("Спокойствие", "Джаз, акустика, чилл", listOf("jazz", "acoustic", "chill", "soul", "folk", "blues", "lounge", "bossa", "soft", "джаз")),
    ROMANCE("Романтика", "R&B, соул, баллады", listOf("r&b", "rnb", "soul", "ballad", "love", "romance", "slow")),
    NOSTALGIA("Ностальгия", "Музыка 10+ лет назад и ретро", listOf("oldies", "retro", "80s", "90s", "70s", "60s", "ретро", "шансон")),
    SLEEP("Сон", "Тихие спокойные жанры", listOf("ambient", "sleep", "new age", "meditation", "piano", "lo-fi", "lofi", "classical")),
}

/**
 * Локальные рекомендации без интернета: похожие треки и подборки по настроению.
 * Учитываются теги (исполнитель, жанр, альбом, год), избранное и то, что вы слушали вместе.
 */
object Recommender {

    fun similar(seed: Track, lib: Library, history: List<HistoryEntry>, favorites: Set<Long>, limit: Int = 50): List<Track> {
        val coListen = coListenCounts(seed.id, history)
        val playCount = history.groupingBy { it.trackId }.eachCount()
        val seedGenre = seed.genre.lowercase().trim()
        return lib.tracks.asSequence()
            .filter { it.id != seed.id }
            .map { t ->
                var s = 0.0
                if (t.artist == seed.artist && seed.artist != "Неизвестный исполнитель") s += 4.0
                if (seedGenre.isNotEmpty() && t.genre.lowercase().trim() == seedGenre) s += 3.0
                else if (seedGenre.isNotEmpty() && t.genre.isNotBlank() && sharesWord(t.genre, seedGenre)) s += 1.5
                if (t.albumId == seed.albumId) s += 1.0
                if (seed.year > 0 && t.year > 0 && abs(seed.year - t.year) <= 3) s += 1.0
                if (t.folderPath == seed.folderPath) s += 0.5
                s += (coListen[t.id] ?: 0) * 2.0
                if (t.id in favorites) s += 0.7
                s += minOf(playCount[t.id] ?: 0, 10) * 0.08
                s += Math.random() * 0.3 // немного разнообразия
                t to s
            }
            .filter { it.second >= 1.0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Сколько раз трек звучал рядом с seed (в пределах 45 минут в одной сессии). */
    private fun coListenCounts(seedId: Long, history: List<HistoryEntry>): Map<Long, Int> {
        val sorted = history.sortedBy { it.time }
        val res = HashMap<Long, Int>()
        sorted.forEachIndexed { i, e ->
            if (e.trackId != seedId) return@forEachIndexed
            for (j in maxOf(0, i - 3)..minOf(sorted.lastIndex, i + 3)) {
                val o = sorted[j]
                if (o.trackId != seedId && abs(o.time - e.time) < 45 * 60_000L) res[o.trackId] = (res[o.trackId] ?: 0) + 1
            }
        }
        return res
    }

    private fun sharesWord(a: String, b: String): Boolean {
        val wa = a.lowercase().split(Regex("[^\\p{L}\\p{N}&]+")).filter { it.length > 2 }.toSet()
        val wb = b.lowercase().split(Regex("[^\\p{L}\\p{N}&]+")).filter { it.length > 2 }.toSet()
        return wa.intersect(wb).isNotEmpty()
    }

    fun mood(kind: MoodKind, lib: Library, history: List<HistoryEntry>, favorites: Set<Long>, limit: Int = 80): List<Track> {
        val nowYear = Calendar.getInstance().get(Calendar.YEAR)
        val plays = history.groupingBy { it.trackId }.eachCount()
        return lib.tracks.asSequence()
            .map { t ->
                val g = t.genre.lowercase()
                var s = 0.0
                if (g.isNotBlank() && kind.keywords.any { g.contains(it) }) s += 3.0
                if (kind == MoodKind.NOSTALGIA && t.year in 1900..(nowYear - 10)) s += 3.0
                if (s > 0) {
                    if (t.id in favorites) s += 1.0
                    s += minOf(plays[t.id] ?: 0, 10) * 0.1
                    s += Math.random() * 1.5
                }
                t to s
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }
}
