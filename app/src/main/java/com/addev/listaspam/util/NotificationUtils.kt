package com.addev.listaspam.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.addev.listaspam.R

private const val NOTIFICATION_CHANNEL_ID = "NOTIFICATION_CHANNEL"
private const val NOTIFICATION_ID = 1

fun sendNotification(context: Context, number: String) {
    createNotificationChannel(context)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED ||
        !shouldShowNotification(context)
    ) {
        // Aquí deberías solicitar el permiso si no está concedido.
        return
    }

    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(context.getString(R.string.notification_title_spam_blocked))
        .setContentText(context.getString(R.string.notification_text_spam_blocked, number))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
}

fun createNotificationChannel(context: Context) {
    val name = context.getString(R.string.notification_channel_name)
    val descriptionText = context.getString(R.string.notification_channel_name)
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
        description = descriptionText
    }

    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}