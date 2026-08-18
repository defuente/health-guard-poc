package cl.defuente.healthguard.alerts

import cl.defuente.healthguard.domain.AlertEvent
import cl.defuente.healthguard.domain.OxygenAlertEvent
import cl.defuente.healthguard.monitoring.AlertSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RemoteAlertSender {
    suspend fun sendTest(settings: AlertSettings): Result<Unit> = send(
        settings = settings,
        eventType = "test",
        latestValue = null,
        minimumValue = null,
        durationMinutes = null
    )

    suspend fun sendHeartRateAlert(settings: AlertSettings, event: AlertEvent): Result<Unit> = send(
        settings = settings,
        eventType = "heart_rate_low",
        latestValue = event.latestBpm.toDouble(),
        minimumValue = event.minimumBpm.toDouble(),
        durationMinutes = event.durationMinutes
    )

    suspend fun sendOxygenAlert(settings: AlertSettings, event: OxygenAlertEvent): Result<Unit> = send(
        settings = settings,
        eventType = "oxygen_low",
        latestValue = event.latestPercent,
        minimumValue = event.minimumPercent,
        durationMinutes = event.durationMinutes
    )

    private suspend fun send(
        settings: AlertSettings,
        eventType: String,
        latestValue: Double?,
        minimumValue: Double?,
        durationMinutes: Long?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.deliveryEnabled) { "El envío remoto está desactivado" }
            require(settings.backendUrl.startsWith("https://")) {
                "La URL del backend debe comenzar con https://"
            }
            require(settings.deviceToken.isNotBlank()) { "Falta el token del dispositivo" }

            val destination = settings.phoneNumber.trim().replace(" ", "").replace("-", "")
            require(destination.matches(Regex("^\\+[1-9][0-9]{7,14}$"))) {
                "Número inválido. Usa formato internacional, por ejemplo +56912345678"
            }

            val payload = JSONObject().apply {
                put("channel", settings.deliveryChannel.wireValue)
                put("to", destination)
                put("personName", settings.personName.ifBlank { "Persona monitoreada" })
                put("eventType", eventType)
                if (latestValue != null) put("latestValue", latestValue)
                if (minimumValue != null) put("minimumValue", minimumValue)
                if (durationMinutes != null) put("durationMinutes", durationMinutes)
            }

            val connection = (URL(settings.backendUrl.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${settings.deviceToken.trim()}")
                setRequestProperty("Accept", "application/json")
            }

            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(status in 200..299) {
                    "Backend respondió HTTP $status${if (responseBody.isBlank()) "" else ": $responseBody"}"
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        fun describeChannel(settings: AlertSettings): String = when (settings.deliveryChannel) {
            DeliveryChannel.SMS -> "SMS"
            DeliveryChannel.WHATSAPP -> "WhatsApp"
        }

        fun formatValue(value: Double): String = String.format(Locale.US, "%.0f", value)
    }
}

enum class DeliveryChannel(val wireValue: String) {
    SMS("sms"),
    WHATSAPP("whatsapp");

    companion object {
        fun fromWireValue(value: String?): DeliveryChannel =
            entries.firstOrNull { it.wireValue == value } ?: SMS
    }
}
