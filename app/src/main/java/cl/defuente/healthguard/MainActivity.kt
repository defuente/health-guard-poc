package cl.defuente.healthguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import cl.defuente.healthguard.alerts.SmsAlertSender
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.monitoring.HealthMonitoringService
import cl.defuente.healthguard.monitoring.MonitorPreferences
import cl.defuente.healthguard.ui.HealthGuardApp

class MainActivity : ComponentActivity() {
    private lateinit var repository: HealthConnectRepository
    private lateinit var monitorPreferences: MonitorPreferences
    private lateinit var smsAlertSender: SmsAlertSender
    private var permissionRefreshVersion by mutableIntStateOf(0)
    private var smsPermissionGranted by mutableStateOf(false)
    private var startMonitoringAfterNotificationPermission = false

    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        permissionRefreshVersion++
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (startMonitoringAfterNotificationPermission) {
            startMonitoringAfterNotificationPermission = false
            if (granted) HealthMonitoringService.start(this)
        }
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsPermissionGranted = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HealthConnectRepository(applicationContext)
        monitorPreferences = MonitorPreferences(applicationContext)
        smsAlertSender = SmsAlertSender(applicationContext)
        smsPermissionGranted = hasSmsPermission()

        setContent {
            MaterialTheme {
                HealthGuardApp(
                    repository = repository,
                    monitorPreferences = monitorPreferences,
                    permissionRefreshVersion = permissionRefreshVersion,
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestPermissions = {
                        permissionsLauncher.launch(repository.permissionsToRequest())
                    },
                    onRequestSmsPermission = {
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    },
                    onSendTestSms = { phone, personName ->
                        smsAlertSender.sendTestMessage(phone, personName).fold(
                            onSuccess = { "SMS de prueba solicitado correctamente." },
                            onFailure = { "Error al enviar SMS: ${it.message ?: it::class.simpleName}" }
                        )
                    },
                    onStartMonitoring = { startMonitoringWithNotificationPermission() },
                    onStopMonitoring = { HealthMonitoringService.stop(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        smsPermissionGranted = hasSmsPermission()
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    private fun startMonitoringWithNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            HealthMonitoringService.start(this)
            return
        }

        startMonitoringAfterNotificationPermission = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
