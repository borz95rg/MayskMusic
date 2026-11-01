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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) { null }
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
        } catch (e: Exception) {
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
                } catch (e: Exception) {
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
        } catch (e: Exception) {
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

    // ------------------------------------------------------------------
    // Обрезка и извлечение аудио
    // ------------------------------------------------------------------

    /**
     * Обрезает трек [startMs]..[endMs].
     * [asCopy] = true → новый файл «название (обрезка).ext» рядом / в Music;
     * false → пытается заменить оригинал (на Android 10+ часто нужна копия).
     */
    /**
     * @param format "m4a" (AAC) или "mp3" (на практике AAC в контейнере; расширение .m4a если mp3-энкодера нет)
     * @param customName имя без расширения; null → из тегов
     */
    fun trimTrack(
        context: Context,
        t: Track,
        startMs: Long,
        endMs: Long,
        asCopy: Boolean,
        customName: String? = null,
        format: String = "m4a",
    ): OpResult {
        trimRangeError(startMs, endMs, t.durationMs)?.let { return OpResult(it) }
        return try {
            val tmpIn = copyToCache(context, t, "trim-in") ?: return OpResult("Не удалось прочитать файл")
            val base = safeFileName(
                customName?.takeIf { it.isNotBlank() } ?: t.title.take(40).ifBlank { t.fileName.substringBeforeLast('.') },
                fallback = "Обрезка",
            )
            val mp3 = format == "mp3"
            val ext = if (mp3) "mp3" else "m4a"
            val displayName = if (asCopy) "$base (обрезка).$ext" else "$base.$ext"
            try {
                saveProcessed(context, "trim-out", displayName, ext, if (mp3) "audio/mpeg" else "audio/mp4") { out ->
                    if (mp3) encodeMp3(tmpIn, out, startMs, endMs) else cutAudioFile(tmpIn, out, startMs, endMs)
                }
            } finally {
                tmpIn.delete()
            }
        } catch (e: Exception) {
            OpResult("Ошибка обрезки: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Общая схема: создать временный .m4a, заполнить его через [produce] (вернёт текст ошибки или null),
     * сохранить в Music/Maysk и удалить временный файл.
     */
    private fun saveProcessed(
        context: Context,
        prefix: String,
        displayName: String,
        ext: String = "m4a",
        mime: String = "audio/mp4",
        produce: (File) -> String?,
    ): OpResult {
        val out = File(context.cacheDir, "$prefix-${System.currentTimeMillis()}.$ext")
        return try {
            val err = produce(out)
            if (err != null) OpResult(err) else saveAudioToMusic(context, out, displayName, mime)
        } finally {
            out.delete()
        }
    }

    /**
     * Извлекает аудиодорожку из видео [videoUri] и сохраняет как .m4a в Music.
     */
    fun extractAudioFromVideo(
        context: Context,
        videoUri: android.net.Uri,
        displayName: String?,
        format: String = "m4a",
    ): OpResult {
        return try {
            val tmpIn = File(context.cacheDir, "vid-in-${System.currentTimeMillis()}")
            try {
                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    tmpIn.outputStream().use { input.copyTo(it) }
                } ?: return OpResult("Не удалось открыть видео")
                val base = safeFileName(
                    displayName?.substringBeforeLast('.'),
                    fallback = "Из видео ${System.currentTimeMillis()}",
                    maxLength = 50,
                )
                if (format == "mp3") {
                    saveProcessed(context, "vid-out", "$base.mp3", "mp3", "audio/mpeg") { out -> encodeMp3(tmpIn, out) }
                } else {
                    saveProcessed(context, "vid-out", "$base.m4a") { out -> extractAudioTrack(tmpIn, out) }
                }
            } finally {
                tmpIn.delete()
            }
        } catch (e: Exception) {
            OpResult("Ошибка извлечения: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun saveAudioToMusic(context: Context, src: File, displayName: String, mime: String): OpResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Maysk/")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = cr.insert(collection, values) ?: return OpResult("Не удалось создать файл")
                try {
                    cr.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("нет записи")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    cr.update(uri, values, null, null)
                    OpResult(null, listOf(uri.toString()))
                } catch (e: Exception) {
                    cr.delete(uri, null, null)
                    throw e
                }
            } else {
                val dir = File(primaryPrefix + "Music/Maysk/").apply { mkdirs() }
                val dest = uniqueFile(dir, displayName)
                src.copyTo(dest, overwrite = false)
                OpResult(null, listOf(dest.absolutePath))
            }
        } catch (e: Exception) {
            OpResult("Сохранение: ${e.message ?: e.javaClass.simpleName}")
        }
    }


    /**
     * Кодирует аудио (или аудиодорожку видео) в MP3 через FFmpegKit (libmp3lame), 192 кбит/с.
     * Если заданы [startMs] и [endMs] — вырезает этот фрагмент. Возвращает текст ошибки или null.
     */
    private fun encodeMp3(input: File, output: File, startMs: Long? = null, endMs: Long? = null): String? {
        val args = mutableListOf("-y")
        if (startMs != null && endMs != null) {
            args += listOf("-ss", String.format(java.util.Locale.US, "%.3f", startMs / 1000.0))
            args += listOf("-t", String.format(java.util.Locale.US, "%.3f", (endMs - startMs) / 1000.0))
        }
        args += listOf(
            "-i", input.absolutePath,
            "-vn", "-c:a", "libmp3lame", "-b:a", "192k", "-ar", "44100", "-ac", "2",
            output.absolutePath,
        )
        return try {
            val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(args.toTypedArray())
            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)) null
            else "Не удалось закодировать MP3 (код ${session.returnCode?.value})"
        } catch (e: Throwable) {
            "Ошибка MP3-кодера: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Обрезка: remux AAC/M4A или перекодирование в AAC (для MP3 и др.). */
    private fun cutAudioFile(input: File, output: File, startMs: Long, endMs: Long): String? {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return "В файле нет аудиодорожки"
            val format = extractor.getTrackFormat(trackIdx)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            extractor.selectTrack(trackIdx)
            if (output.exists()) output.delete()

            // AAC в MP4-контейнере можно просто вырезать без перекодирования
            if (mime.contains("mp4a") || mime == "audio/mp4") {
                val err = remuxSegment(extractor, format, output, startMs, endMs)
                if (err == null) return null
            }
            extractor.release()
            return reencodeSegment(input, output, startMs, endMs)
        } catch (e: Exception) {
            return "Обрезка не удалась (${e.message})"
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun remuxSegment(
        extractor: android.media.MediaExtractor,
        format: android.media.MediaFormat,
        output: File,
        startMs: Long,
        endMs: Long,
    ): String? {
        return try {
            extractor.seekTo(startMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val muxer = android.media.MediaMuxer(output.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()
            val buf = java.nio.ByteBuffer.allocate(512 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            val endUs = endMs * 1000
            var firstUs = -1L
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buf, 0)
                if (info.size < 0) break
                val pts = extractor.sampleTime
                if (pts > endUs) break
                if (firstUs < 0) firstUs = pts
                info.presentationTimeUs = (pts - firstUs).coerceAtLeast(0)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buf, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            if (output.length() < 100) "Пустой файл" else null
        } catch (e: Exception) {
            e.message ?: "remux failed"
        }
    }

    /** Декод → AAC 128k → M4A. Работает с MP3 и большинством форматов. */
    private fun reencodeSegment(input: File, output: File, startMs: Long, endMs: Long): String? {
        val extractor = android.media.MediaExtractor()
        var decoder: android.media.MediaCodec? = null
        var encoder: android.media.MediaCodec? = null
        var muxer: android.media.MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return "Нет аудиодорожки"
            val inFormat = extractor.getTrackFormat(trackIdx)
            val inMime = inFormat.getString(android.media.MediaFormat.KEY_MIME) ?: return "Неизвестный формат"
            extractor.selectTrack(trackIdx)
            extractor.seekTo(startMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val sampleRate = if (inFormat.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE))
                inFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channelCount = if (inFormat.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                inFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT) else 2

            decoder = android.media.MediaCodec.createDecoderByType(inMime)
            decoder.configure(inFormat, null, null, 0)
            decoder.start()

            val outFormat = android.media.MediaFormat.createAudioFormat(
                android.media.MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount,
            ).apply {
                setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(android.media.MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            encoder = android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val endUs = endMs * 1000L
            var muxerTrack = -1
            var muxerStarted = false
            var inputDone = false
            var outputDone = false
            val timeoutUs = 10_000L
            var firstPts = -1L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buf = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (sampleSize < 0 || pts > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                // decoder → encoder
                val decInfo = android.media.MediaCodec.BufferInfo()
                val outIndex = decoder.dequeueOutputBuffer(decInfo, timeoutUs)
                if (outIndex >= 0) {
                    val decBuf = decoder.getOutputBuffer(outIndex)!!
                    if (decInfo.size > 0 && decInfo.presentationTimeUs <= endUs) {
                        var remaining = decInfo.size
                        var offset = decInfo.offset
                        while (remaining > 0) {
                            val encIn = encoder.dequeueInputBuffer(timeoutUs)
                            if (encIn >= 0) {
                                val encBuf = encoder.getInputBuffer(encIn)!!
                                val chunk = minOf(remaining, encBuf.capacity())
                                encBuf.clear()
                                decBuf.position(offset)
                                decBuf.limit(offset + chunk)
                                encBuf.put(decBuf)
                                val ptsAdj = if (firstPts < 0) {
                                    firstPts = decInfo.presentationTimeUs
                                    0L
                                } else (decInfo.presentationTimeUs - firstPts).coerceAtLeast(0)
                                encoder.queueInputBuffer(encIn, 0, chunk, ptsAdj, 0)
                                offset += chunk
                                remaining -= chunk
                            } else break
                        }
                    }
                    val eos = decInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (eos) {
                        val encIn = encoder.dequeueInputBuffer(timeoutUs)
                        if (encIn >= 0) {
                            encoder.queueInputBuffer(encIn, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }

                val encInfo = android.media.MediaCodec.BufferInfo()
                val encOut = encoder.dequeueOutputBuffer(encInfo, timeoutUs)
                when {
                    encOut == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxer = android.media.MediaMuxer(output.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                            muxerTrack = muxer!!.addTrack(encoder.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                    }
                    encOut >= 0 -> {
                        val encBuf = encoder.getOutputBuffer(encOut)!!
                        if (encInfo.size > 0 && muxerStarted &&
                            encInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            muxer!!.writeSampleData(muxerTrack, encBuf, encInfo)
                        }
                        val eos = encInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(encOut, false)
                        if (eos) outputDone = true
                    }
                }
            }

            try { muxer?.stop() } catch (_: Throwable) {}
            muxer?.release()
            if (output.length() < 100) "Пустой результат перекодирования" else null
        } catch (e: Exception) {
            "Перекодирование: ${e.message}"
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Throwable) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun extractAudioTrack(input: File, output: File): String? {
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return "В видео нет аудиодорожки"
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            if (output.exists()) output.delete()
            val muxer = android.media.MediaMuxer(output.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()
            val buf = java.nio.ByteBuffer.allocate(256 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buf, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buf, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            if (output.length() < 100) return "Пустой результат"
            null
        } catch (e: Exception) {
            "Не удалось извлечь аудио: ${e.message}"
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }
}
