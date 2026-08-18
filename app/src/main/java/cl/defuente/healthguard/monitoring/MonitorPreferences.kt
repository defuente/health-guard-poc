package cl.defuente.healthguard.monitoring

import android.content.Context
import cl.defuente.healthguard.domain.AlertRule
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

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }

    fun isMonitoringEnabled(): Boolean = prefs.getBoolean(KEY_MONITORING_ENABLED, false)

    fun setLastCheck(at: Instant, bpm: Int?, source: String?) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK_AT, at.toEpochMilli())
            .apply {
                if (bpm == null) remove(KEY_LAST_BPM) else putInt(KEY_LAST_BPM, bpm)
                if (source == null) remove(KEY_LAST_SOURCE) else putString(KEY_LAST_SOURCE, source)
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

    fun setAlertActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_ACTIVE, active).apply()
    }

    fun snapshot(): MonitorSnapshot {
        val lastCheckMillis = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val hasLastBpm = prefs.contains(KEY_LAST_BPM)
        return MonitorSnapshot(
            enabled = isMonitoringEnabled(),
            lastCheckAt = lastCheckMillis.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            lastBpm = if (hasLastBpm) prefs.getInt(KEY_LAST_BPM, 0) else null,
            lastSource = prefs.getString(KEY_LAST_SOURCE, null),
            alertActive = prefs.getBoolean(KEY_ALERT_ACTIVE, false),
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_DURATION = "duration"
        private const val KEY_MIN_READINGS = "minimum_readings"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_LAST_BPM = "last_bpm"
        private const val KEY_LAST_SOURCE = "last_source"
        private const val KEY_ALERT_ACTIVE = "alert_active"
        private const val KEY_LAST_ERROR = "last_error"
    }
}

data class MonitorSnapshot(
    val enabled: Boolean,
    val lastCheckAt: Instant?,
    val lastBpm: Int?,
    val lastSource: String?,
    val alertActive: Boolean,
    val lastError: String?
)
