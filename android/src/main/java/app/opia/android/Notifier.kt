package app.opia.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import java.util.concurrent.ThreadLocalRandom

class Notifier(var context: Context) {
    private var gNM: NotificationManager? = null
    private var channelId = "Opia-Android" // TODO per account

    init {
        channelId = context.packageName
        createNotificationChannel()
        gNM = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun sendNotification(title: String, text: String, priority: Int) {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            Notification.Builder(context)
        }

        val notification = notificationBuilder.setSmallIcon(R.mipmap.ic_launcher).setTicker(text)
            .setWhen(System.currentTimeMillis()).setContentTitle(title).setContentText(text)
            .setPriority(priority).build()

        gNM!!.notify(ThreadLocalRandom.current().nextInt(), notification)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId.isNotEmpty()) {
            val name = context.packageName
            val descriptionText = "Opia"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
