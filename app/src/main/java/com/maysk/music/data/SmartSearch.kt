package com.maysk.music.data

/**
 * Поиск «по смыслу» для голосовых команд, Google Ассистента и Android Auto:
 * «включи Imagine Dragons», «альбом Evolve», «плейлист В машине», «любимое».
 */
object SmartSearch {
    data class Result(val tracks: List<Track>, val description: String)

    private val fillers = listOf(
        "включи", "включить", "поставь", "играй", "сыграй", "воспроизведи", "запусти", "хочу послушать", "послушать",
        "play", "listen to", "песню", "песни", "трек", "треки", "музыку", "группы", "группу", "исполнителя",
        "на maysk music", "в maysk music", "maysk music", "maysk",
    )

    fun normalize(s: String): String = s.lowercase().replace('ё', 'е').replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun cleanQuery(raw: String): String {
        var q = " " + normalize(raw) + " "
        fillers.sortedByDescending { it.length }.forEach { q = q.replace(" ${normalize(it)} ", " ") }
        return q.trim()
    }

    fun find(lib: Library, playlists: List<Playlist>, favorites: Set<Long>, raw: String): Result {
        val q = cleanQuery(raw)
        if (q.isEmpty()) return Result(lib.tracks.shuffled(), "Вся музыка вперемешку")

        if (q.startsWith("любим") || q == "избранное" || q == "favorites") {
            return Result(lib.tracks.filter { it.id in favorites }.shuffled(), "Любимое")
        }
        Regex("^(плейлист|playlist) (.+)$").find(q)?.let { m ->
            val name = m.groupValues[2]
            playlists.firstOrNull { normalize(it.name).contains(name) }?.let { p ->
                return Result(p.trackIds.mapNotNull { lib.byId[it] }, "Плейлист «${p.name}»")
            }
        }
        Regex("^(альбом|album) (.+)$").find(q)?.let { m ->
            val name = m.groupValues[2]
            lib.albums.firstOrNull { normalize(it.title).contains(name) }?.let { a ->
                return Result(a.tracks, "Альбом «${a.title}»")
            }
        }
        // Точное совпадение с исполнителем
        lib.artists.firstOrNull { normalize(it.name) == q }?.let { return Result(it.tracks.shuffled(), it.name) }
        // Плейлист по имени
        playlists.firstOrNull { normalize(it.name) == q }?.let { p ->
            return Result(p.trackIds.mapNotNull { lib.byId[it] }, "Плейлист «${p.name}»")
        }
        // Трек целиком («believer imagine dragons»)
        val words = q.split(' ').filter { it.isNotBlank() }
        val scored = lib.tracks.map { t ->
            val hay = normalize("${t.title} ${t.artist} ${t.album}")
            val title = normalize(t.title)
            var score = words.count { hay.contains(it) } * 2
            if (title == q) score += 20
            if (hay.contains(q)) score += 6
            if (normalize(t.artist).contains(q)) score += 4
            t to score
        }.filter { it.second >= words.size * 2 }.sortedByDescending { it.second }
        if (scored.isNotEmpty()) {
            val best = scored.first().first
            // Если запрос похож на исполнителя — играем все его треки, начиная с лучшего
            val artist = lib.artists.firstOrNull { normalize(it.name).contains(q) || q.contains(normalize(it.name)) }
            if (artist != null && normalize(best.title) != q) {
                return Result(listOf(best) + artist.tracks.filter { it.id != best.id }.shuffled(), artist.name)
            }
            return Result(scored.map { it.first }.take(50), "${best.title} — ${best.artist}")
        }
        // Жанр
        lib.genres.firstOrNull { normalize(it.name).contains(q) }?.let { return Result(it.tracks.shuffled(), "Жанр ${it.name}") }
        return Result(emptyList(), "Ничего не найдено по запросу «$raw»")
    }
}
