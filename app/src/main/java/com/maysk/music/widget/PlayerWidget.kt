package com.maysk.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.maysk.music.AppContainer
import com.maysk.music.MainActivity
import com.maysk.music.MayskApp
import com.maysk.music.R

/** Виджет рабочего стола: обложка, название, назад / play-pause / вперёд. */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext as MayskApp
        updateAll(context, app.container)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as MayskApp
        val c = app.container
        when (intent.action) {
            ACTION_TOGGLE, ACTION_NEXT, ACTION_PREV -> {
                val exo = c.exoPlayer
                if (exo == null || exo.mediaItemCount == 0) {
                    // Плеер не запущен — открываем приложение, оно восстановит очередь.
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_AUTOPLAY, intent.action == ACTION_TOGGLE)
                    )
                    return
                }
                when (intent.action) {
                    ACTION_TOGGLE -> if (exo.isPlaying) exo.pause() else { if (exo.playbackState == androidx.media3.common.Player.STATE_IDLE) exo.prepare(); exo.play() }
                    ACTION_NEXT -> exo.seekToNext()
                    ACTION_PREV -> exo.seekToPrevious()
                }
                updateAll(context, c)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.maysk.music.widget.TOGGLE"
        const val ACTION_NEXT = "com.maysk.music.widget.NEXT"
        const val ACTION_PREV = "com.maysk.music.widget.PREV"

        private var lastArtKey: String? = null
        private var lastArt: Bitmap? = null

        fun updateAll(context: Context, c: AppContainer) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = try {
                manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            } catch (e: Exception) { return }
            if (ids.isEmpty()) return

            val exo = c.exoPlayer
            val item = exo?.currentMediaItem
            val meta = item?.mediaMetadata
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setTextViewText(R.id.widget_title, meta?.title ?: context.getString(R.string.nothing_playing))
            views.setTextViewText(R.id.widget_artist, meta?.artist ?: context.getString(R.string.app_name))
            views.setImageViewResource(R.id.widget_play, if (exo?.isPlaying == true) R.drawable.ic_w_pause else R.drawable.ic_w_play)

            val artUri = meta?.artworkUri
            val key = artUri?.toString()
            if (key != lastArtKey) {
                lastArtKey = key
                lastArt = artUri?.let { uri ->
                    try {
                        context.contentResolver.openInputStream(uri)?.use { s ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                            BitmapFactory.decodeStream(s, null, opts)
                        }
                    } catch (e: Exception) { null }
                }
            }
            val art = lastArt
            if (art != null) views.setImageViewBitmap(R.id.widget_art, art)
            else views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)

            views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, ACTION_PREV, 3))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 4, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            try { manager.updateAppWidget(ids, views) } catch (_: Exception) { }
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, PlayerWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
