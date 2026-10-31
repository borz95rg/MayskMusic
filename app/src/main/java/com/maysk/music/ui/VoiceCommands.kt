package com.maysk.music.ui

import com.maysk.music.AppContainer
import com.maysk.music.data.SmartSearch

/**
 * Голосовые команды внутри приложения (распознавание речи делает система Android):
 * «пауза», «дальше», «назад», «продолжи», «перемешай всё», «любимое»,
 * «таймер 30 минут», «включи Imagine Dragons», «альбом Evolve», «плейлист В машине».
 */
object VoiceCommands {
    private val pauseWords = listOf("пауза", "стоп", "останови", "остановить", "выключи музыку", "pause", "stop")
    private val nextWords = listOf("дальше", "следующ", "next", "skip", "пропусти")
    private val prevWords = listOf("назад", "предыдущ", "previous", "верни")
    private val resumeWords = listOf("продолж", "играй", "resume", "play")
    private val shuffleWords = listOf("перемешай", "случайн", "shuffle", "что-нибудь", "что нибудь", "любую музыку")

    fun handle(raw: String, c: AppContainer): String {
        val n = SmartSearch.normalize(raw)
        val p = c.player
        if (n.isBlank()) return "Команда не распознана"

        if (pauseWords.any { n == it || n.startsWith("$it ") }) { p.pause(); return "Пауза" }
        if (nextWords.any { n.startsWith(it) }) { p.next(); return "Следующий трек" }
        if (prevWords.any { n.startsWith(it) }) { p.previous(); return "Предыдущий трек" }
        if (resumeWords.any { n == it }) {
            if (!p.state.value.isPlaying) p.togglePlay()
            return "Продолжаю"
        }
        Regex("таймер (?:на )?(\\d{1,3})").find(n)?.let { m ->
            val min = m.groupValues[1].toInt().coerceIn(1, 600)
            c.sleepTimer.start(min)
            return "Таймер сна: $min мин"
        }
        val lib = c.library.library.value
        if (shuffleWords.any { n.contains(it) } && n.split(' ').size <= 3) {
            p.playTracks(lib.tracks, shuffle = true)
            return "Перемешиваю всю музыку"
        }
        val r = SmartSearch.find(lib, c.userData.playlists.value, c.userData.favorites.value, raw)
        if (r.tracks.isEmpty()) return r.description
        p.playTracks(r.tracks)
        return "▶ ${r.description}"
    }
}
