package cl.defuente.healthguard.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cl.defuente.healthguard.MainActivity
import cl.defuente.healthguard.domain.AlertEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthAlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    init {
        createChannels()
    }

    fun monitoringNotification(latestBpm: Int? = null, latestAt: Instant? = null): Notification {
        val detail = when {
            latestBpm != null && latestAt != null -> "Última FC: $latestBpm BPM · ${timeFormatter.format(latestAt)}"
            else -> "Esperando nuevas lecturas de Health Connect"
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Health Guard está monitoreando")
            .setContentText(detail)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showLowHeartRateAlert(event: AlertEvent) {
        val text = "${event.latestBpm} BPM · mínima ${event.minimumBpm} · ${event.durationMinutes} min"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Frecuencia cardíaca baja")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Health Guard detectó una frecuencia cardíaca bajo el umbral configurado durante ${event.durationMinutes} minutos. " +
                        "Última: ${event.latestBpm} BPM. Mínima: ${event.minimumBpm} BPM."
                )
            )
            .setContentIntent(openAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ALERT_ID, notification) }
    }

    fun showRecovery(bpm: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Frecuencia cardíaca recuperada")
            .setContentText("La última lectura volvió al rango configurado: $bpm BPM")
            .setContentIntent(openAppPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_RECOVERY_ID, notification) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITORING,
                "Monitoreo de salud",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente mientras Health Guard monitorea Health Connect"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Alertas de salud",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas configuradas de frecuencia cardíaca"
                enableVibration(true)
            }
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val NOTIFICATION_MONITORING_ID = 2001
        private const val NOTIFICATION_ALERT_ID = 2002
        private const val NOTIFICATION_RECOVERY_ID = 2003
        private const val CHANNEL_MONITORING = "health_guard_monitoring"
        private const val CHANNEL_ALERTS = "health_guard_alerts"
    }
}
