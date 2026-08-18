package cl.defuente.healthguard.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cl.defuente.healthguard.alerts.SmsAlertSender
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.domain.AlertRuleEngine
import cl.defuente.healthguard.domain.OxygenAlertRuleEngine
import cl.defuente.healthguard.notifications.HealthAlertNotifier
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HealthMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null
    private lateinit var repository: HealthConnectRepository
    private lateinit var preferences: MonitorPreferences
    private lateinit var notifier: HealthAlertNotifier
    private lateinit var smsSender: SmsAlertSender

    override fun onCreate() {
        super.onCreate()
        repository = HealthConnectRepository(applicationContext)
        preferences = MonitorPreferences(applicationContext)
        notifier = HealthAlertNotifier(applicationContext)
        smsSender = SmsAlertSender(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null && !preferences.isMonitoringEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        preferences.setMonitoringEnabled(true)

        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                HealthAlertNotifier.NOTIFICATION_MONITORING_ID,
                notifier.monitoringNotification(),
                type
            )
        } catch (error: SecurityException) {
            preferences.setError("Android no permitió iniciar el monitoreo: ${error.message ?: "falta un permiso"}")
            preferences.setMonitoringEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitoringJob?.isActive != true) {
            monitoringJob = serviceScope.launch { monitoringLoop() }
        }
        return START_STICKY
    }

    private suspend fun monitoringLoop() {
        while (serviceScope.isActive && preferences.isMonitoringEnabled()) {
            runCatching { performCheck() }
                .onFailure { preferences.setError(it.message ?: it::class.simpleName ?: "Error desconocido") }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun performCheck() {
        check(repository.isAvailable()) { "Health Connect no está disponible" }
        check(repository.hasBackgroundReadPermission()) { "Falta permiso para leer Health Connect en segundo plano" }

        val hasHeartPermission = repository.hasHeartRateReadPermission()
        val hasOxygenPermission = repository.hasOxygenSaturationReadPermission()
        check(hasHeartPermission || hasOxygenPermission) { "Falta permiso para leer signos vitales" }

        val heartReadings = if (hasHeartPermission) repository.readRecentHeartRate(hours = 3) else emptyList()
        val oxygenReadings = if (hasOxygenPermission) repository.readRecentOxygenSaturation(hours = 3) else emptyList()

        val latestHeart = heartReadings.maxByOrNull { it.timestamp }
        val latestOxygen = oxygenReadings.maxByOrNull { it.timestamp }
        preferences.setLastCheck(
            at = Instant.now(),
            bpm = latestHeart?.bpm,
            heartRateSource = latestHeart?.source,
            oxygenPercent = latestOxygen?.percentage,
            oxygenSource = latestOxygen?.source
        )

        val snapshot = preferences.snapshot()
        val alertSettings = preferences.loadAlertSettings()

        if (hasHeartPermission) {
            val heartRule = preferences.loadRule()
            val heartAlert = AlertRuleEngine.evaluate(heartReadings, heartRule)
            if (heartAlert != null && !snapshot.heartAlertActive) {
                preferences.setHeartAlertActive(true)
                notifier.showLowHeartRateAlert(heartAlert)
                maybeSendSms(
                    alertSettings,
                    smsSender.sendHeartRateAlert(alertSettings.phoneNumber, alertSettings.personName, heartAlert)
                )
            } else if (
                heartAlert == null && snapshot.heartAlertActive && latestHeart != null &&
                latestHeart.bpm >= heartRule.lowHeartRateThresholdBpm
            ) {
                preferences.setHeartAlertActive(false)
                notifier.showHeartRateRecovery(latestHeart.bpm)
            }
        }

        if (hasOxygenPermission) {
            val oxygenRule = preferences.loadOxygenRule()
            val oxygenAlert = OxygenAlertRuleEngine.evaluate(oxygenReadings, oxygenRule)
            if (oxygenAlert != null && !snapshot.oxygenAlertActive) {
                preferences.setOxygenAlertActive(true)
                notifier.showLowOxygenAlert(oxygenAlert)
                maybeSendSms(
                    alertSettings,
                    smsSender.sendOxygenAlert(alertSettings.phoneNumber, alertSettings.personName, oxygenAlert)
                )
            } else if (
                oxygenAlert == null && snapshot.oxygenAlertActive && latestOxygen != null &&
                latestOxygen.percentage >= oxygenRule.lowOxygenThresholdPercent
            ) {
                preferences.setOxygenAlertActive(false)
                notifier.showOxygenRecovery(latestOxygen.percentage)
            }
        }

        val notification = notifier.monitoringNotification(
            latestBpm = latestHeart?.bpm,
            latestHeartRateAt = latestHeart?.timestamp,
            latestOxygenPercent = latestOxygen?.percentage
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            HealthAlertNotifier.NOTIFICATION_MONITORING_ID,
            notification,
            type
        )
    }

    private fun maybeSendSms(settings: AlertSettings, result: Result<Unit>) {
        if (!settings.smsEnabled || settings.phoneNumber.isBlank()) return
        result.fold(
            onSuccess = { preferences.setSmsStatus("Último SMS de alerta solicitado correctamente") },
            onFailure = { preferences.setSmsStatus("Error SMS: ${it.message ?: it::class.simpleName}") }
        )
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "cl.defuente.healthguard.action.START_MONITORING"
        private const val POLL_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            MonitorPreferences(context).setMonitoringEnabled(true)
            val intent = Intent(context, HealthMonitoringService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            MonitorPreferences(context).apply {
                setMonitoringEnabled(false)
                setHeartAlertActive(false)
                setOxygenAlertActive(false)
            }
            context.stopService(Intent(context, HealthMonitoringService::class.java))
        }
    }
}
