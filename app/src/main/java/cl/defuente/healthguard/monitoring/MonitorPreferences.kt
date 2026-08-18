package cl.defuente.healthguard.monitoring

import android.content.Context
import cl.defuente.healthguard.alerts.DeliveryChannel
import cl.defuente.healthguard.domain.AlertRule
import cl.defuente.healthguard.domain.OxygenAlertRule
import java.time.Instant

class MonitorPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "health_guard_monitor",
        Context.MODE_PRIVATE
    )

    fun loadRule(): AlertRule = AlertRule(
        lowHeartRateThresholdBpm = prefs.getInt(KEY_THRESHOLD, 45),
        minimumDurationMinutes = prefs.getLong(KEY_DURATION, 10),
        minimumReadings = prefs.getInt(KEY_MIN_READINGS, 5),
        maximumGapBetweenReadingsMinutes = 5,
        maximumLatestReadingAgeMinutes = 10
    )

    fun saveRule(rule: AlertRule) {
        prefs.edit()
            .putInt(KEY_THRESHOLD, rule.lowHeartRateThresholdBpm)
            .putLong(KEY_DURATION, rule.minimumDurationMinutes)
            .putInt(KEY_MIN_READINGS, rule.minimumReadings)
            .apply()
    }

    fun loadOxygenRule(): OxygenAlertRule = OxygenAlertRule(
        lowOxygenThresholdPercent = prefs.getFloat(KEY_OXYGEN_THRESHOLD, 90f).toDouble(),
        minimumDurationMinutes = prefs.getLong(KEY_OXYGEN_DURATION, 5),
        minimumReadings = prefs.getInt(KEY_OXYGEN_MIN_READINGS, 2),
        maximumGapBetweenReadingsMinutes = 10,
        maximumLatestReadingAgeMinutes = 15
    )

    fun saveOxygenRule(rule: OxygenAlertRule) {
        prefs.edit()
            .putFloat(KEY_OXYGEN_THRESHOLD, rule.lowOxygenThresholdPercent.toFloat())
            .putLong(KEY_OXYGEN_DURATION, rule.minimumDurationMinutes)
            .putInt(KEY_OXYGEN_MIN_READINGS, rule.minimumReadings)
            .apply()
    }

    fun loadAlertSettings(): AlertSettings = AlertSettings(
        personName = prefs.getString(KEY_PERSON_NAME, "Persona monitoreada") ?: "Persona monitoreada",
        phoneNumber = prefs.getString(KEY_PHONE_NUMBER, "") ?: "",
        deliveryEnabled = prefs.getBoolean(
            KEY_DELIVERY_ENABLED,
            prefs.getBoolean(KEY_LEGACY_SMS_ENABLED, false)
        ),
        deliveryChannel = DeliveryChannel.fromWireValue(prefs.getString(KEY_DELIVERY_CHANNEL, null)),
        backendUrl = prefs.getString(KEY_BACKEND_URL, "") ?: "",
        deviceToken = prefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
    )

    fun saveAlertSettings(settings: AlertSettings) {
        prefs.edit()
            .putString(KEY_PERSON_NAME, settings.personName)
            .putString(KEY_PHONE_NUMBER, settings.phoneNumber)
            .putBoolean(KEY_DELIVERY_ENABLED, settings.deliveryEnabled)
            .putString(KEY_DELIVERY_CHANNEL, settings.deliveryChannel.wireValue)
            .putString(KEY_BACKEND_URL, settings.backendUrl)
            .putString(KEY_DEVICE_TOKEN, settings.deviceToken)
            .apply()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun isMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_MONITORING_ENABLED, false)

    fun setLastCheck(
        at: Instant,
        bpm: Int?,
        heartRateSource: String?,
        oxygenPercent: Double?,
        oxygenSource: String?
    ) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK_AT, at.toEpochMilli())
            .apply {
                if (bpm == null) remove(KEY_LAST_BPM) else putInt(KEY_LAST_BPM, bpm)
                if (heartRateSource == null) remove(KEY_LAST_SOURCE) else putString(KEY_LAST_SOURCE, heartRateSource)
                if (oxygenPercent == null) remove(KEY_LAST_OXYGEN) else putFloat(KEY_LAST_OXYGEN, oxygenPercent.toFloat())
                if (oxygenSource == null) remove(KEY_LAST_OXYGEN_SOURCE) else putString(KEY_LAST_OXYGEN_SOURCE, oxygenSource)
            }
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun setError(message: String) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK_AT, Instant.now().toEpochMilli())
            .putString(KEY_LAST_ERROR, message)
            .apply()
    }

    fun setHeartAlertActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_HEART_ALERT_ACTIVE, active).apply()
    }

    fun setOxygenAlertActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_OXYGEN_ALERT_ACTIVE, active).apply()
    }

    fun setDeliveryStatus(message: String) {
        prefs.edit().putString(KEY_LAST_DELIVERY_STATUS, message).apply()
    }

    fun snapshot(): MonitorSnapshot {
        val lastCheckMillis = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val hasLastBpm = prefs.contains(KEY_LAST_BPM)
        val hasLastOxygen = prefs.contains(KEY_LAST_OXYGEN)
        return MonitorSnapshot(
            enabled = isMonitoringEnabled(),
            lastCheckAt = lastCheckMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            lastBpm = if (hasLastBpm) prefs.getInt(KEY_LAST_BPM, 0) else null,
            lastSource = prefs.getString(KEY_LAST_SOURCE, null),
            lastOxygenPercent = if (hasLastOxygen) prefs.getFloat(KEY_LAST_OXYGEN, 0f).toDouble() else null,
            lastOxygenSource = prefs.getString(KEY_LAST_OXYGEN_SOURCE, null),
            heartAlertActive = prefs.getBoolean(KEY_HEART_ALERT_ACTIVE, false),
            oxygenAlertActive = prefs.getBoolean(KEY_OXYGEN_ALERT_ACTIVE, false),
            lastError = prefs.getString(KEY_LAST_ERROR, null),
            lastDeliveryStatus = prefs.getString(
                KEY_LAST_DELIVERY_STATUS,
                prefs.getString(KEY_LEGACY_LAST_SMS_STATUS, null)
            )
        )
    }

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_DURATION = "duration"
        private const val KEY_MIN_READINGS = "minimum_readings"
        private const val KEY_OXYGEN_THRESHOLD = "oxygen_threshold"
        private const val KEY_OXYGEN_DURATION = "oxygen_duration"
        private const val KEY_OXYGEN_MIN_READINGS = "oxygen_minimum_readings"
        private const val KEY_PERSON_NAME = "person_name"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_DELIVERY_ENABLED = "delivery_enabled"
        private const val KEY_DELIVERY_CHANNEL = "delivery_channel"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_LEGACY_SMS_ENABLED = "sms_enabled"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_LAST_BPM = "last_bpm"
        private const val KEY_LAST_SOURCE = "last_source"
        private const val KEY_LAST_OXYGEN = "last_oxygen"
        private const val KEY_LAST_OXYGEN_SOURCE = "last_oxygen_source"
        private const val KEY_HEART_ALERT_ACTIVE = "heart_alert_active"
        private const val KEY_OXYGEN_ALERT_ACTIVE = "oxygen_alert_active"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_DELIVERY_STATUS = "last_delivery_status"
        private const val KEY_LEGACY_LAST_SMS_STATUS = "last_sms_status"
    }
}

data class AlertSettings(
    val personName: String,
    val phoneNumber: String,
    val deliveryEnabled: Boolean,
    val deliveryChannel: DeliveryChannel,
    val backendUrl: String,
    val deviceToken: String
)

data class MonitorSnapshot(
    val enabled: Boolean,
    val lastCheckAt: Instant?,
    val lastBpm: Int?,
    val lastSource: String?,
    val lastOxygenPercent: Double?,
    val lastOxygenSource: String?,
    val heartAlertActive: Boolean,
    val oxygenAlertActive: Boolean,
    val lastError: String?,
    val lastDeliveryStatus: String?
)
