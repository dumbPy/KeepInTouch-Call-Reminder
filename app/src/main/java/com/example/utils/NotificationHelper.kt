package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "agenda_notifications_channel"
    const val CHANNEL_NAME = "Call Agenda Reminders"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Daily digest and reminders for pending calls"
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendAgendaTestNotification(
        context: Context,
        pendingCount: Int,
        contactNames: List<String>
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val collapsedText = if (pendingCount > 0) {
            val top2 = contactNames.take(2).joinToString(", ")
            val suffix = if (pendingCount > 2) ", ..." else ""
            "$pendingCount contact(s) on agenda: $top2$suffix"
        } else {
            "All caught up! No pending calls for today."
        }

        val expandedText = if (pendingCount > 0) {
            val top5 = contactNames.take(5)
            val sb = StringBuilder()
            sb.append("Contacts due on agenda ($pendingCount):\n")
            top5.forEach { name ->
                sb.append("• ").append(name).append("\n")
            }
            val remaining = pendingCount - top5.size
            if (remaining > 0) {
                sb.append("+ $remaining others")
            }
            sb.toString().trim()
        } else {
            "Great job! You have no overdue or pending calls today."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (pendingCount > 0) "📞 Daily Call Agenda ($pendingCount Due)" else "📞 Daily Call Agenda")
            .setContentText(collapsedText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
