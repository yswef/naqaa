package com.naqaa.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import com.naqaa.app.R
import com.naqaa.app.ui.Screens

/**
 * Notifications carry neutral wording and are hidden from the lock screen, because the
 * shade is visible to anyone holding the phone. The sensitive content of a message is
 * only ever shown inside the unlocked application.
 */
object Notices {

    const val ID_PROTECTION = 10
    const val ID_BLOCK = 20
    const val ID_PRAYER = 30
    const val ID_ADHKAR = 31
    const val ID_MOTIVATION = 32

    fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            // The prayer reminder carries the bundled chime, so it has a channel of its own:
            // a channel keeps the sound it was created with, and the other reminders stay
            // quiet while a blocked attempt in the middle of browsing stays quiet too.
            NotificationChannel(CHANNEL_PRAYER, context.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
                setSound(
                    chime(context),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROTECTION, context.getString(R.string.channel_protection), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
        )
    }

    /**
     * Address of the bundled chime. Naming the resource here is also what keeps it in a
     * release build: the build removes every resource the code does not mention.
     */
    private fun chime(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.prayer_chime}")

    fun protection(context: Context): Notification = builder(context, CHANNEL_PROTECTION)
        .setContentTitle(context.getString(R.string.notification_title))
        .setContentText(context.getString(R.string.protection_running))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(screen(context, Screens.HOME, 1))
        .addAction(action(context, R.string.emergency, screen(context, Screens.EMERGENCY, 2)))
        .addAction(action(context, R.string.protection_pause, screen(context, Screens.PROTECTION, 3)))
        .build()

    fun protectionStarted(context: Context, verse: String, motivation: String) {
        post(
            context, ID_BLOCK + 1, builder(context, CHANNEL_REMINDER)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(motivation)
                .setStyle(Notification.BigTextStyle().bigText("$motivation\n\n$verse"))
                .setAutoCancel(true)
                .setContentIntent(screen(context, Screens.HOME, 4))
                .addAction(action(context, R.string.emergency, screen(context, Screens.EMERGENCY, 5)))
                .build()
        )
    }

    fun blocked(context: Context, verse: String, motivation: String) {
        post(
            context, ID_BLOCK, builder(context, CHANNEL_REMINDER)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(motivation)
                .setStyle(Notification.BigTextStyle().bigText("$motivation\n\n$verse"))
                .setAutoCancel(true)
                .setContentIntent(screen(context, Screens.EMERGENCY, 6))
                .addAction(action(context, R.string.emergency, screen(context, Screens.EMERGENCY, 7)))
                .build()
        )
    }

    fun prayerReminder(context: Context, prayer: String) {
        post(
            context, ID_PRAYER, builder(context, CHANNEL_PRAYER)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.prayer_reminder_text, prayer))
                .setAutoCancel(true)
                .setContentIntent(screen(context, Screens.PRAYER, 8))
                .build()
        )
    }

    fun adhkarReminder(context: Context, morning: Boolean) {
        val text = if (morning) R.string.adhkar_morning_reminder else R.string.adhkar_evening_reminder
        post(
            context, ID_ADHKAR, builder(context, CHANNEL_REMINDER)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(text))
                .setAutoCancel(true)
                .setContentIntent(screen(context, Screens.PRAYER, 9))
                .build()
        )
    }

    fun motivation(context: Context, text: String) {
        post(
            context, ID_MOTIVATION, builder(context, CHANNEL_REMINDER)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(screen(context, Screens.HOME, 10))
                .addAction(action(context, R.string.emergency, screen(context, Screens.EMERGENCY, 11)))
                .build()
        )
    }

    private fun builder(context: Context, channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setColor(context.getColor(R.color.brand))
            .setShowWhen(false)

    private fun action(context: Context, title: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), context.getString(title), intent).build()

    private fun screen(context: Context, screen: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Screens.intent(context, screen),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun post(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    private const val CHANNEL_PRAYER = "prayer"
    private const val CHANNEL_REMINDER = "reminders"
    private const val CHANNEL_PROTECTION = "protection"
}
