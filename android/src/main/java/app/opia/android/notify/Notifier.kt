package app.opia.android.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.opia.android.R
import app.opia.common.db.ListUnread

class Notifier(var context: Context) {
    private var gNM: NotificationManagerCompat? = null
    private var channelId = "Opia-Android" // TODO per account ("instance", but need handle)

    init {
        //channelId = context.packageName
        createNotificationChannel()
        gNM = NotificationManagerCompat.from(context)
    }

    fun notifyUnread(title: String, unread: List<ListUnread>, priority: Int) {
        val person: Person = Person.Builder().setName("self?").build()
        val messageStyle = NotificationCompat.MessagingStyle(person)
        var txt = ""
        unread.groupBy { it.name }.forEach { (name, unreads) ->
            val person: Person = Person.Builder().setName(name).build()
            unreads.forEach { unread ->
                messageStyle.addMessage(unread.payload, System.currentTimeMillis(), person)
                txt += "$name: ${unread.payload}"
            }
        }

        val notification =
            NotificationCompat.Builder(context, channelId).setSmallIcon(R.mipmap.ic_launcher)
                .setWhen(System.currentTimeMillis()).setContentTitle(title).setContentText(txt)
                .setStyle(messageStyle).setPriority(priority).build()

        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        gNM!!.notify(NOTIFICATION_ID_UNREAD, notification)
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

    companion object {
        private const val NOTIFICATION_ID_UNREAD = 101;
    }
}
