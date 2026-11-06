package com.maysk.design

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Связь приложений экосистемы Maysk между собой. */
object MayskEcosystem {
    const val FILES = "com.maysk.files"
    const val MUSIC = "com.maysk.music"
    /** Путь к папке, которую нужно открыть в Maysk Files. */
    const val EXTRA_PATH = "open_path"

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    /** Открыть папку (или папку, где лежит файл) в Maysk Files. */
    fun showInFiles(context: Context, path: String): Boolean {
        val f = java.io.File(path)
        val dir = if (f.isDirectory) f.absolutePath else (f.parentFile?.absolutePath ?: return false)
        val intent = Intent(Intent.ACTION_MAIN)
            .setClassName(FILES, "$FILES.MainActivity")
            .putExtra(EXTRA_PATH, dir)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return start(context, intent)
    }

    /** Воспроизвести аудиофайл в Maysk Music. */
    fun playInMusic(context: Context, uri: Uri, mime: String = "audio/*"): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .setPackage(MUSIC)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    /** Открыть страницу загрузки приложения экосистемы (GitHub Releases). */
    fun openDownload(context: Context, repo: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repo/releases/latest")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }
}
