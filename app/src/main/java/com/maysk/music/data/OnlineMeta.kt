package com.maysk.music.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Бесплатные открытые базы музыкальных данных (без ключей и регистрации):
 *  • MusicBrainz — названия, исполнители, альбомы, годы;
 *  • Cover Art Archive — обложки альбомов.
 * Используется только по кнопке пользователя. Вызывать из фонового потока.
 */
object OnlineMeta {
    private const val UA = "MayskMusic/1.0 ( https://github.com/maysk-music )"
    private var lastRequest = 0L

    data class Suggestion(
        val title: String,
        val artist: String,
        val album: String,
        val year: String,
        val trackNo: String,
        val releaseId: String?,
    )

    /** MusicBrainz просит не чаще 1 запроса в секунду. */
    @Synchronized
    private fun throttle() {
        val wait = 1100 - (System.currentTimeMillis() - lastRequest)
        if (wait > 0) Thread.sleep(wait)
        lastRequest = System.currentTimeMillis()
    }

    private fun get(url: String): String {
        throttle()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode == 503) throw IllegalStateException("сервис занят, попробуйте позже")
            if (conn.responseCode !in 200..299) throw IllegalStateException("ответ ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun esc(s: String) = s.replace(Regex("[+\\-&|!(){}\\[\\]^\"~*?:\\\\/]"), " ").trim()

    /** Поиск записи по названию и (необязательно) исполнителю. */
    fun searchRecordings(title: String, artist: String?): List<Suggestion> {
        val q = buildString {
            append("recording:\"").append(esc(title)).append('"')
            if (!artist.isNullOrBlank()) append(" AND artist:\"").append(esc(artist)).append('"')
        }
        val json = JSONObject(get("https://musicbrainz.org/ws/2/recording/?fmt=json&limit=10&query=" + URLEncoder.encode(q, "UTF-8")))
        val recs = json.optJSONArray("recordings") ?: return emptyList()
        val out = ArrayList<Suggestion>()
        for (i in 0 until recs.length()) {
            val r = recs.getJSONObject(i)
            val credits = r.optJSONArray("artist-credit")
            val artistName = buildString {
                if (credits != null) for (j in 0 until credits.length()) {
                    val c = credits.getJSONObject(j)
                    append(c.optString("name"))
                    append(c.optString("joinphrase"))
                }
            }
            val releases = r.optJSONArray("releases")
            val rel = releases?.optJSONObject(0)
            val trackNo = rel?.optJSONArray("media")?.optJSONObject(0)?.optJSONArray("track")?.optJSONObject(0)?.optString("number").orEmpty()
            out += Suggestion(
                title = r.optString("title"),
                artist = artistName,
                album = rel?.optString("title").orEmpty(),
                year = rel?.optString("date").orEmpty().ifEmpty { r.optString("first-release-date") }.take(4),
                trackNo = trackNo,
                releaseId = rel?.optString("id")?.takeIf { it.isNotBlank() },
            )
        }
        return out.distinctBy { "${it.title}|${it.artist}|${it.album}" }
    }

    /** ID релиза альбома в MusicBrainz. */
    fun findRelease(album: String, artist: String?): String? {
        val q = buildString {
            append("release:\"").append(esc(album)).append('"')
            if (!artist.isNullOrBlank() && artist != "Неизвестный исполнитель") append(" AND artist:\"").append(esc(artist)).append('"')
        }
        val json = JSONObject(get("https://musicbrainz.org/ws/2/release/?fmt=json&limit=5&query=" + URLEncoder.encode(q, "UTF-8")))
        val arr = json.optJSONArray("releases") ?: return null
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            if (r.optInt("score", 0) >= 80) return r.optString("id").takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Скачивает обложку релиза в память приложения. Возвращает путь к файлу или null. */
    fun downloadCover(context: Context, releaseId: String, key: String): String? {
        var url = URL("https://coverartarchive.org/release/$releaseId/front-500")
        var conn = url.openConnection() as HttpURLConnection
        var redirects = 0
        try {
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", UA)
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            while (conn.responseCode in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = URL(url, loc)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", UA)
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                redirects++
            }
            if (conn.responseCode !in 200..299) return null
            val dir = File(context.filesDir, "albumart").apply { mkdirs() }
            val f = File(dir, "${key}_${System.currentTimeMillis()}.jpg")
            conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            return if (f.length() > 1000) f.absolutePath else { f.delete(); null }
        } finally {
            conn.disconnect()
        }
    }

    /** Есть ли у альбома встроенная/системная обложка. */
    fun hasSystemArt(context: Context, albumId: Long): Boolean = try {
        context.contentResolver.openFileDescriptor(systemAlbumArtUri(albumId), "r")?.use { true } ?: false
    } catch (e: Throwable) { false }
}
