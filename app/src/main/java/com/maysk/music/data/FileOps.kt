package com.maysk.music.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Поля тегов, которые можно редактировать. */
enum class TagField(val label: String, val key: FieldKey) {
    TITLE("Название", FieldKey.TITLE),
    ARTIST("Исполнитель", FieldKey.ARTIST),
    ALBUM("Альбом", FieldKey.ALBUM),
    ALBUM_ARTIST("Исполнитель альбома", FieldKey.ALBUM_ARTIST),
    GENRE("Жанр", FieldKey.GENRE),
    YEAR("Год", FieldKey.YEAR),
    TRACK("№ трека", FieldKey.TRACK),
}

/**
 * Операции с файлами: запись тегов, перемещение и копирование.
 * Все вызовы — из фонового потока и только после подтверждения пользователя
 * (на Android 11+ система дополнительно показывает свой запрос на изменение файлов).
 */
/** Результат файловой операции: текст ошибки (или null) и пути, которые нужно пересканировать. */
data class OpResult(val error: String?, val scan: List<String> = emptyList())

object FileOps {
    init {
        runCatching { Logger.getLogger("org.jaudiotagger").level = Level.OFF }
    }

    private val TAG_EXT = setOf("mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "mp4", "aac", "wav", "wma", "aif", "aiff", "dsf")

    fun canEditTags(t: Track): Boolean = t.extension in TAG_EXT

    /** Читает текущие теги файла (для формы редактирования). Если не удалось — берёт данные из библиотеки. */
    fun readTags(context: Context, t: Track): Map<TagField, String> {
        val fallback = mapOf(
            TagField.TITLE to t.title,
            TagField.ARTIST to t.artist.takeIf { it != "Неизвестный исполнитель" }.orEmpty(),
            TagField.ALBUM to t.album.takeIf { it != "Без альбома" }.orEmpty(),
            TagField.ALBUM_ARTIST to "",
            TagField.GENRE to t.genre,
            TagField.YEAR to if (t.year > 0) t.year.toString() else "",
            TagField.TRACK to if (t.trackNo > 0) t.trackNo.toString() else "",
        )
        return try {
            val tmp = copyToCache(context, t, "read") ?: return fallback
            val tag = AudioFileIO.read(tmp).tag ?: return fallback
            val res = TagField.entries.associateWith { f -> runCatching { tag.getFirst(f.key) }.getOrNull().orEmpty() }
            tmp.delete()
            res.mapValues { (k, v) -> v.ifBlank { if (k == TagField.ALBUM_ARTIST) "" else fallback[k].orEmpty() } }
        } catch (e: Throwable) {
            fallback
        }
    }

    /**
     * Записывает теги. Пустое значение в [changes] удаляет поле, отсутствующий ключ — оставляет как есть.
     * Возвращает null при успехе или текст ошибки.
     */
    fun writeTagsOp(context: Context, t: Track, changes: Map<TagField, String>): OpResult =
        OpResult(writeTags(context, t, changes), listOf(t.path))

    fun writeTags(context: Context, t: Track, changes: Map<TagField, String>): String? {
        if (!canEditTags(t)) return "Формат .${t.extension} не поддерживает редактирование тегов"
        val tmp = copyToCache(context, t, "edit") ?: return "Не удалось прочитать файл"
        return try {
            val af = AudioFileIO.read(tmp)
            val tag = af.tagOrCreateAndSetDefault
            changes.forEach { (field, value) ->
                val v = value.trim()
                if (v.isEmpty()) runCatching { tag.deleteField(field.key) }
                else tag.setField(field.key, v)
            }
            af.commit()
            val out = context.contentResolver.openOutputStream(t.uri, "wt") ?: return "Нет доступа к файлу"
            out.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            null
        } catch (e: SecurityException) {
            "Нет разрешения на изменение файла"
        } catch (e: Throwable) {
            "Ошибка записи тегов: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            tmp.delete()
        }
    }

    private fun copyToCache(context: Context, t: Track, prefix: String): File? {
        return try {
            val dir = File(context.cacheDir, "tags").apply { mkdirs() }
            val f = File(dir, "$prefix-${t.id}.${t.extension.ifEmpty { "mp3" }}")
            val copied = context.contentResolver.openInputStream(t.uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            if (copied == null) null else f
        } catch (e: Throwable) { null }
    }

    /** Обновить запись в MediaStore после изменения файла. */
    suspend fun rescanFiles(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCoroutine { cont ->
            var left = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                synchronized(this) {
                    left--
                    if (left == 0) cont.resume(Unit)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Перемещение и копирование
    // ------------------------------------------------------------------

    /** Папки верхнего уровня, в которые Android разрешает класть аудиофайлы. */
    val AUDIO_ROOTS = listOf("Music", "Download", "Podcasts", "Audiobooks", "Recordings", "Ringtones", "Alarms", "Notifications")

    private val primaryPrefix: String
        get() = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"

    /** "Music/Rock/" для пути "/storage/emulated/0/Music/Rock/song.mp3"; null — если файл не во внутренней памяти. */
    fun relativeDir(path: String): String? {
        if (!path.startsWith(primaryPrefix)) return null
        val rel = path.removePrefix(primaryPrefix).substringBeforeLast('/', "")
        return if (rel.isEmpty()) null else "$rel/"
    }

    fun isAllowedTarget(rel: String): Boolean = AUDIO_ROOTS.any { rel.startsWith("$it/") }

    /** Возможные папки назначения: существующие папки с музыкой + стандартные. */
    fun targetFolders(lib: Library): List<String> {
        val existing = lib.allFolders.mapNotNull { f -> f.tracks.firstOrNull()?.let { relativeDir(it.path) } }
        return (listOf("Music/", "Download/") + existing).filter { isAllowedTarget(it) }.distinct().sorted()
    }

    fun sanitizeFolder(name: String): String =
        name.trim().replace(Regex("[\\\\:*?\"<>|]"), "_").trim('/').ifEmpty { "Новая папка" }

    /** Перемещает файл. Для Android 10+ нужно разрешение на запись (createWriteRequest). */
    fun move(context: Context, t: Track, targetRel: String): OpResult {
        if (!isAllowedTarget(targetRel)) return OpResult("Android не разрешает хранить музыку в этой папке")
        if (relativeDir(t.path) == targetRel) return OpResult(null)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, targetRel) }
                val n = context.contentResolver.update(t.uri, values, null, null)
                OpResult(if (n > 0) null else "Не удалось переместить «${t.fileName}»")
            } else {
                val dir = File(primaryPrefix + targetRel).apply { mkdirs() }
                val dest = uniqueFile(dir, t.fileName)
                if (File(t.path).renameTo(dest)) OpResult(null, listOf(t.path, dest.absolutePath))
                else OpResult("Не удалось переместить «${t.fileName}»")
            }
        } catch (e: SecurityException) {
            OpResult("Нет разрешения на изменение файла")
        } catch (e: Throwable) {
            OpResult("Ошибка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Копирует файл в папку. Новый файл принадлежит приложению — отдельное разрешение на Android 10+ не нужно. */
    fun copy(context: Context, t: Track, targetRel: String): OpResult {
        if (!isAllowedTarget(targetRel)) return OpResult("Android не разрешает хранить музыку в этой папке")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, t.fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, t.mime.ifEmpty { "audio/mpeg" })
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetRel)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val newUri = cr.insert(collection, values) ?: return OpResult("Не удалось создать файл")
                try {
                    val out = cr.openOutputStream(newUri) ?: throw IllegalStateException("нет доступа")
                    out.use { o -> cr.openInputStream(t.uri)?.use { it.copyTo(o) } ?: throw IllegalStateException("файл недоступен") }
                    cr.update(newUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Throwable) {
                    runCatching { cr.delete(newUri, null, null) }
                    throw e
                }
                OpResult(null)
            } else {
                val dir = File(primaryPrefix + targetRel).apply { mkdirs() }
                val dest = uniqueFile(dir, t.fileName)
                File(t.path).copyTo(dest)
                OpResult(null, listOf(dest.absolutePath))
            }
        } catch (e: SecurityException) {
            OpResult("Нет разрешения на запись")
        } catch (e: Throwable) {
            OpResult("Ошибка копирования: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun legacyTargetPath(targetRel: String, fileName: String): String = primaryPrefix + targetRel + fileName

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    // ------------------------------------------------------------------
    // Восстановление тегов из имени файла
    // ------------------------------------------------------------------

    data class FilenameGuess(val track: Track, val artist: String, val title: String, val trackNo: Int?)

    /** «01. Artist - Title.mp3» / «Artist — Title.mp3» → исполнитель и название. */
    fun guessFromFilename(t: Track): FilenameGuess? {
        val base = t.fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s*[\\[(](official|lyrics|audio|video|hq|hd|320|kbps)[^\\])]*[\\])]", RegexOption.IGNORE_CASE), "")
            .trim()
        val m = Regex("^(?:(\\d{1,3})[.\\s-]+)?(.+?)\\s+[-–—]\\s+(.+)$").find(base) ?: return null
        val no = m.groupValues[1].toIntOrNull()
        val artist = m.groupValues[2].trim()
        val title = m.groupValues[3].trim()
        if (artist.isEmpty() || title.isEmpty()) return null
        return FilenameGuess(t, artist, title, no)
    }

    /** Треки, у которых теги, похоже, не заполнены, а имя файла содержит «Исполнитель - Название». */
    fun tagRepairCandidates(lib: Library): List<FilenameGuess> = lib.tracks.mapNotNull { t ->
        val missingArtist = t.artist == "Неизвестный исполнитель"
        val titleIsFilename = t.title == t.fileName.substringBeforeLast('.')
        if (!missingArtist && !titleIsFilename) return@mapNotNull null
        guessFromFilename(t)?.takeIf { g -> !(g.artist == t.artist && g.title == t.title) }
    }
}
