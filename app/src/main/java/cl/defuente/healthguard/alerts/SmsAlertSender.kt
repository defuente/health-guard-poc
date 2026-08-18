package cl.defuente.healthguard.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import cl.defuente.healthguard.domain.AlertEvent
import cl.defuente.healthguard.domain.OxygenAlertEvent
import java.util.Locale

class SmsAlertSender(context: Context) {
    private val appContext = context.applicationContext

    fun sendTestMessage(phone: String, personName: String): Result<Unit> = runCatching {
        send(
            phone,
            "Health Guard POC: SMS de prueba para alertas de ${personName.ifBlank { "persona monitoreada" }}. " +
                "Si recibes este mensaje, el canal SMS funciona."
        )
    }

    fun sendHeartRateAlert(phone: String, personName: String, event: AlertEvent): Result<Unit> = runCatching {
        send(
            phone,
            "Health Guard ALERTA - ${personName.ifBlank { "persona monitoreada" }}: " +
                "FC baja ${event.latestBpm} BPM (mínima ${event.minimumBpm}) durante ${event.durationMinutes} min. " +
                "Verifica su estado."
        )
    }

    fun sendOxygenAlert(phone: String, personName: String, event: OxygenAlertEvent): Result<Unit> = runCatching {
        val latest = String.format(Locale.US, "%.0f", event.latestPercent)
        val minimum = String.format(Locale.US, "%.0f", event.minimumPercent)
        send(
            phone,
            "Health Guard ALERTA - ${personName.ifBlank { "persona monitoreada" }}: " +
                "SpO2 baja $latest% (mínima $minimum%) durante ${event.durationMinutes} min. " +
                "Verifica su estado."
        )
    }

    private fun send(phone: String, message: String) {
        check(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Falta permiso SEND_SMS" }

        check(appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            "Este dispositivo no puede enviar SMS"
        }

        val destination = phone.trim().replace(" ", "").replace("-", "")
        require(destination.matches(Regex("^\\+?[0-9]{8,15}$"))) {
            "Número inválido. Usa formato internacional, por ejemplo +56912345678"
        }

        @Suppress("DEPRECATION")
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        if (parts.size <= 1) {
            smsManager.sendTextMessage(destination, null, message, null, null)
        } else {
            smsManager.sendMultipartTextMessage(destination, null, ArrayList(parts), null, null)
        }
    }
}
