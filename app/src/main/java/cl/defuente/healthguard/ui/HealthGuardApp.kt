package cl.defuente.healthguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.domain.AlertRule
import cl.defuente.healthguard.domain.AlertRuleEngine
import cl.defuente.healthguard.domain.HeartRateReading
import cl.defuente.healthguard.monitoring.MonitorPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun HealthGuardApp(
    repository: HealthConnectRepository,
    monitorPreferences: MonitorPreferences,
    permissionRefreshVersion: Int,
    onRequestPermissions: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hasHeartRatePermission by remember { mutableStateOf(false) }
    var hasBackgroundPermission by remember { mutableStateOf(false) }
    var readings by remember { mutableStateOf<List<HeartRateReading>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var simulationEnabled by remember { mutableStateOf(false) }
    val initialRule = remember { monitorPreferences.loadRule() }
    var thresholdText by remember { mutableStateOf(initialRule.lowHeartRateThresholdBpm.toString()) }
    var durationText by remember { mutableStateOf(initialRule.minimumDurationMinutes.toString()) }
    var minimumReadingsText by remember { mutableStateOf(initialRule.minimumReadings.toString()) }
    var monitorSnapshot by remember { mutableStateOf(monitorPreferences.snapshot()) }

    suspend fun refreshPermissionState() {
        hasHeartRatePermission = repository.hasHeartRateReadPermission()
        hasBackgroundPermission = repository.hasBackgroundReadPermission()
    }

    fun refreshReadings() {
        scope.launch {
            loading = true
            errorMessage = null
            runCatching {
                refreshPermissionState()
                if (hasHeartRatePermission) {
                    readings = repository.readRecentHeartRate()
                }
            }.onFailure {
                errorMessage = it.message ?: it::class.simpleName
            }
            loading = false
        }
    }

    LaunchedEffect(permissionRefreshVersion) {
        if (repository.isAvailable()) refreshPermissionState()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            monitorSnapshot = monitorPreferences.snapshot()
            delay(1_000)
        }
    }

    val rule = AlertRule(
        lowHeartRateThresholdBpm = thresholdText.toIntOrNull()?.coerceIn(30, 100) ?: 45,
        minimumDurationMinutes = durationText.toLongOrNull()?.coerceIn(1, 120) ?: 10,
        minimumReadings = minimumReadingsText.toIntOrNull()?.coerceIn(2, 60) ?: 5
    )

    LaunchedEffect(rule) {
        monitorPreferences.saveRule(rule)
    }

    val displayedReadings = if (simulationEnabled) simulatedLowReadings() else readings
    val alert = AlertRuleEngine.evaluate(displayedReadings, rule)
    val latest = displayedReadings.maxByOrNull { it.timestamp }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Health Guard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("POC de monitoreo familiar con Health Connect · v0.2")

            StatusCard(
                healthConnectStatus = repository.statusLabel(),
                hasHeartRatePermission = hasHeartRatePermission,
                hasBackgroundPermission = hasBackgroundPermission,
                backgroundFeatureAvailable = repository.isBackgroundReadFeatureAvailable()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRequestPermissions, enabled = repository.isAvailable()) {
                    Text("Solicitar acceso")
                }
                Button(onClick = { refreshReadings() }, enabled = repository.isAvailable() && !loading) {
                    Text(if (loading) "Leyendo..." else "Actualizar")
                }
            }

            errorMessage?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Monitoreo nocturno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (monitorSnapshot.enabled) "● Activo" else "○ Inactivo")
                    monitorSnapshot.lastCheckAt?.let { Text("Último chequeo: ${timeFormatter.format(it)}") }
                    monitorSnapshot.lastBpm?.let { Text("Última FC en segundo plano: $it BPM") }
                    monitorSnapshot.lastSource?.let { Text("Fuente: $it") }
                    if (monitorSnapshot.alertActive) {
                        Text("⚠ Alerta activa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    monitorSnapshot.lastError?.let {
                        Text("Error de monitoreo: $it", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Mientras esté activo, Android mantendrá una notificación persistente y Health Guard revisará Health Connect aproximadamente una vez por minuto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = if (monitorSnapshot.enabled) onStopMonitoring else onStartMonitoring,
                        enabled = hasHeartRatePermission && hasBackgroundPermission
                    ) {
                        Text(if (monitorSnapshot.enabled) "Detener monitoreo" else "Iniciar monitoreo nocturno")
                    }
                    if (!hasBackgroundPermission) {
                        Text("Autoriza la lectura en segundo plano antes de iniciar el monitoreo.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Frecuencia actual", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = latest?.let { "${it.bpm} BPM" } ?: "Sin datos",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    latest?.let {
                        Text("Última lectura: ${timeFormatter.format(it.timestamp)}")
                        Text("Fuente: ${it.source}")
                    }
                }
            }

            Text("Regla de alerta", style = MaterialTheme.typography.titleLarge)
            Text("Estos valores son configurables para la POC y no constituyen un umbral médico recomendado.")

            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it.filter(Char::isDigit).take(3) },
                label = { Text("Frecuencia mínima (BPM)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it.filter(Char::isDigit).take(3) },
                label = { Text("Duración mínima (minutos)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = minimumReadingsText,
                onValueChange = { minimumReadingsText = it.filter(Char::isDigit).take(2) },
                label = { Text("Cantidad mínima de lecturas") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = { simulationEnabled = !simulationEnabled }) {
                Text(if (simulationEnabled) "Salir de simulación" else "Simular FC baja por 12 min")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (alert != null) {
                        Text("⚠ Alerta detectada", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("Mínima: ${alert.minimumBpm} BPM")
                        Text("Última: ${alert.latestBpm} BPM")
                        Text("Duración: ${alert.durationMinutes} min")
                        Text("Lecturas consecutivas: ${alert.readingsCount}")
                    } else {
                        Text("Sin alerta activa", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Últimas lecturas", style = MaterialTheme.typography.titleLarge)
            if (displayedReadings.isEmpty()) {
                Text("Todavía no hay lecturas disponibles.")
            } else {
                displayedReadings.sortedByDescending { it.timestamp }.take(15).forEach { reading ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(timeFormatter.format(reading.timestamp))
                        Text("${reading.bpm} BPM")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Esta aplicación es una POC de alerta y acompañamiento. No diagnostica enfermedades ni sustituye dispositivos médicos o atención profesional.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusCard(
    healthConnectStatus: String,
    hasHeartRatePermission: Boolean,
    hasBackgroundPermission: Boolean,
    backgroundFeatureAvailable: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estado", style = MaterialTheme.typography.titleMedium)
            Text("Health Connect: $healthConnectStatus")
            Text("Lectura de FC: ${if (hasHeartRatePermission) "autorizada" else "sin autorización"}")
            Text(
                if (!backgroundFeatureAvailable) {
                    "Lectura en segundo plano: no disponible en esta versión de Health Connect"
                } else {
                    "Lectura en segundo plano: ${if (hasBackgroundPermission) "autorizada" else "sin autorización"}"
                }
            )
        }
    }
}

private fun simulatedLowReadings(now: Instant = Instant.now()): List<HeartRateReading> {
    val bpm = listOf(42, 41, 40, 39, 41, 40, 42)
    return bpm.mapIndexed { index, value ->
        HeartRateReading(
            timestamp = now.minusSeconds(((bpm.lastIndex - index) * 120).toLong()),
            bpm = value,
            source = "SIMULACIÓN"
        )
    }
}
