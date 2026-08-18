package cl.defuente.healthguard.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.domain.AlertRuleEngine
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

    override fun onCreate() {
        super.onCreate()
        repository = HealthConnectRepository(applicationContext)
        preferences = MonitorPreferences(applicationContext)
        notifier = HealthAlertNotifier(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

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
        check(repository.hasHeartRateReadPermission()) { "Falta permiso para leer frecuencia cardíaca" }
        check(repository.hasBackgroundReadPermission()) { "Falta permiso para leer Health Connect en segundo plano" }

        val readings = repository.readRecentHeartRate(hours = 3)
        val latest = readings.maxByOrNull { it.timestamp }
        preferences.setLastCheck(Instant.now(), latest?.bpm, latest?.source)

        val rule = preferences.loadRule()
        val alert = AlertRuleEngine.evaluate(readings, rule)
        val snapshot = preferences.snapshot()

        if (alert != null && !snapshot.alertActive) {
            preferences.setAlertActive(true)
            notifier.showLowHeartRateAlert(alert)
        } else if (alert == null && snapshot.alertActive && latest != null && latest.bpm >= rule.lowHeartRateThresholdBpm) {
            preferences.setAlertActive(false)
            notifier.showRecovery(latest.bpm)
        }

        val notification = notifier.monitoringNotification(latest?.bpm, latest?.timestamp)
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

    private fun stopMonitoring() {
        preferences.setMonitoringEnabled(false)
        preferences.setAlertActive(false)
        monitoringJob?.cancel()
        monitoringJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "cl.defuente.healthguard.action.START_MONITORING"
        private const val ACTION_STOP = "cl.defuente.healthguard.action.STOP_MONITORING"
        private const val POLL_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            MonitorPreferences(context).setMonitoringEnabled(true)
            val intent = Intent(context, HealthMonitoringService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            MonitorPreferences(context).setMonitoringEnabled(false)
            val intent = Intent(context, HealthMonitoringService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
