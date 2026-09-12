package com.naqaa.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.naqaa.app.NaqaaApplication
import com.naqaa.app.R
import com.naqaa.app.data.Progress
import com.naqaa.app.ui.Screens
import com.naqaa.app.util.LocaleX
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Home screen counter. It shows a number and a weekday, which reads like a habit or
 * calendar widget, and it deliberately names nothing about the subject of the application.
 */
class ProgressWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        executor.execute {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        private val executor = Executors.newSingleThreadExecutor()

        /** Redraws every placed widget. Called after any change to the journal. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidget::class.java))
            if (ids.isEmpty()) return
            executor.execute { render(context.applicationContext, manager, ids) }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val localized = LocaleX.localized(context)
            val graph = (context.applicationContext as NaqaaApplication).graph
            val count = runCatching {
                val preferences = graph.current()
                Progress.streak(graph.snapshot().events, preferences.startedAt, Instant.now(), ZoneId.systemDefault()).current
            }.getOrDefault(0)
            val caption = DateTimeFormatter.ofPattern("EEEE", localized.resources.configuration.locales[0])
                .format(java.time.LocalDate.now())
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.progress_widget)
                views.setTextViewText(R.id.widget_count, count.toString())
                views.setTextViewText(R.id.widget_caption, caption)
                views.setOnClickPendingIntent(R.id.widget_root, open(context, Screens.HOME, 300))
                views.setOnClickPendingIntent(R.id.widget_emergency, open(context, Screens.EMERGENCY, 301))
                manager.updateAppWidget(id, views)
            }
        }

        private fun open(context: Context, screen: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            context, requestCode,
            Screens.intent(context, screen),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
