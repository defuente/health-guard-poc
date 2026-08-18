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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.defuente.healthguard.alerts.DeliveryChannel
import cl.defuente.healthguard.data.HealthConnectRepository
import cl.defuente.healthguard.domain.AlertRule
import cl.defuente.healthguard.domain.AlertRuleEngine
import cl.defuente.healthguard.domain.HeartRateReading
import cl.defuente.healthguard.domain.OxygenAlertRule
import cl.defuente.healthguard.domain.OxygenAlertRuleEngine
import cl.defuente.healthguard.domain.OxygenSaturationReading
import cl.defuente.healthguard.monitoring.AlertSettings
import cl.defuente.healthguard.monitoring.MonitorPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun HealthGuardApp(
    repository: HealthConnectRepository,
    monitorPreferences: MonitorPreferences,
    permissionRefreshVersion: Int,
    onRequestPermissions: () -> Unit,
    onSendTestAlert: suspend (AlertSettings) -> Result<Unit>,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var hasHeartRatePermission by remember { mutableStateOf(false) }
    var hasOxygenPermission by remember { mutableStateOf(false) }
    var hasBackgroundPermission by remember { mutableStateOf(false) }
    var heartReadings by remember { mutableStateOf<List<HeartRateReading>>(emptyList()) }
    var oxygenReadings by remember { mutableStateOf<List<OxygenSaturationReading>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var heartSimulationEnabled by remember { mutableStateOf(false) }
    var oxygenSimulationEnabled by remember { mutableStateOf(false) }

    val initialHeartRule = remember { monitorPreferences.loadRule() }
    var thresholdText by remember { mutableStateOf(initialHeartRule.lowHeartRateThresholdBpm.toString()) }
    var durationText by remember { mutableStateOf(initialHeartRule.minimumDurationMinutes.toString()) }
    var minimumReadingsText by remember { mutableStateOf(initialHeartRule.minimumReadings.toString()) }

    val initialOxygenRule = remember { monitorPreferences.loadOxygenRule() }
    var oxygenThresholdText by remember { mutableStateOf(initialOxygenRule.lowOxygenThresholdPercent.toInt().toString()) }
    var oxygenDurationText by remember { mutableStateOf(initialOxygenRule.minimumDurationMinutes.toString()) }
    var oxygenMinimumReadingsText by remember { mutableStateOf(initialOxygenRule.minimumReadings.toString()) }

    val initialAlertSettings = remember { monitorPreferences.loadAlertSettings() }
    var personNameText by remember { mutableStateOf(initialAlertSettings.personName) }
    var phoneNumberText by remember { mutableStateOf(initialAlertSettings.phoneNumber) }
    var deliveryEnabled by remember { mutableStateOf(initialAlertSettings.deliveryEnabled) }
    var deliveryChannel by remember { mutableStateOf(initialAlertSettings.deliveryChannel) }
    var backendUrlText by remember { mutableStateOf(initialAlertSettings.backendUrl) }
    var deviceTokenText by remember { mutableStateOf(initialAlertSettings.deviceToken) }
    var deliveryTestStatus by remember { mutableStateOf<String?>(null) }

    var monitorSnapshot by remember { mutableStateOf(monitorPreferences.snapshot()) }

    suspend fun refreshPermissionState() {
        hasHeartRatePermission = repository.hasHeartRateReadPermission()
        hasOxygenPermission = repository.hasOxygenSaturationReadPermission()
        hasBackgroundPermission = repository.hasBackgroundReadPermission()
    }

    fun refreshReadings() {
        scope.launch {
            loading = true
            errorMessage = null
            runCatching {
                refreshPermissionState()
                heartReadings = if (hasHeartRatePermission) repository.readRecentHeartRate() else emptyList()
                oxygenReadings = if (hasOxygenPermission) repository.readRecentOxygenSaturation() else emptyList()
            }.onFailure {
                errorMessage = it.message ?: it::class.simpleName
            }
            loading = false
        }
    }

    LaunchedEffect(permissionRefreshVersion) {
        if (repository.isAvailable()) {
            refreshPermissionState()
            refreshReadings()
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            monitorSnapshot = monitorPreferences.snapshot()
            delay(1_000)
        }
    }

    val heartRule = AlertRule(
        lowHeartRateThresholdBpm = thresholdText.toIntOrNull()?.coerceIn(30, 100) ?: 45,
        minimumDurationMinutes = durationText.toLongOrNull()?.coerceIn(1, 120) ?: 10,
        minimumReadings = minimumReadingsText.toIntOrNull()?.coerceIn(2, 60) ?: 5
    )
    val oxygenRule = OxygenAlertRule(
        lowOxygenThresholdPercent = oxygenThresholdText.toDoubleOrNull()?.coerceIn(50.0, 100.0) ?: 90.0,
        minimumDurationMinutes = oxygenDurationText.toLongOrNull()?.coerceIn(1, 120) ?: 5,
        minimumReadings = oxygenMinimumReadingsText.toIntOrNull()?.coerceIn(2, 30) ?: 2
    )
    val alertSettings = AlertSettings(
        personName = personNameText.take(50),
        phoneNumber = phoneNumberText.take(20),
        deliveryEnabled = deliveryEnabled,
        deliveryChannel = deliveryChannel,
        backendUrl = backendUrlText.trim().take(300),
        deviceToken = deviceTokenText.trim().take(200)
    )

    LaunchedEffect(heartRule) { monitorPreferences.saveRule(heartRule) }
    LaunchedEffect(oxygenRule) { monitorPreferences.saveOxygenRule(oxygenRule) }
    LaunchedEffect(alertSettings) { monitorPreferences.saveAlertSettings(alertSettings) }

    val displayedHeartReadings = if (heartSimulationEnabled) simulatedLowHeartReadings() else heartReadings
    val displayedOxygenReadings = if (oxygenSimulationEnabled) simulatedLowOxygenReadings() else oxygenReadings
    val heartAlert = AlertRuleEngine.evaluate(displayedHeartReadings, heartRule)
    val oxygenAlert = OxygenAlertRuleEngine.evaluate(displayedOxygenReadings, oxygenRule)
    val latestHeart = displayedHeartReadings.maxByOrNull { it.timestamp }
    val latestOxygen = displayedOxygenReadings.maxByOrNull { it.timestamp }
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
            Text("POC de monitoreo familiar · v0.4 · FC + SpO₂ + alertas remotas")

            StatusCard(
                healthConnectStatus = repository.statusLabel(),
                hasHeartRatePermission = hasHeartRatePermission,
                hasOxygenPermission = hasOxygenPermission,
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

            VitalCard(
                title = "Frecuencia cardíaca",
                value = latestHeart?.let { "${it.bpm} BPM" } ?: "Sin datos",
                timestamp = latestHeart?.timestamp,
                source = latestHeart?.source,
                timeFormatter = timeFormatter
            )
            VitalCard(
                title = "Saturación de oxígeno (SpO₂)",
                value = latestOxygen?.let { "${formatPercent(it.percentage)}%" } ?: "Sin datos",
                timestamp = latestOxygen?.timestamp,
                source = latestOxygen?.source,
                timeFormatter = timeFormatter
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entrega de alertas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Las alertas salen por HTTPS hacia un backend; la app ya no solicita permiso SEND_SMS.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = personNameText,
                        onValueChange = { personNameText = it.take(50) },
                        label = { Text("Nombre de la persona monitoreada") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneNumberText,
                        onValueChange = { phoneNumberText = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' }.take(20) },
                        label = { Text("Teléfono destino, ej. +56912345678") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Canal")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = deliveryChannel == DeliveryChannel.SMS,
                            onClick = { deliveryChannel = DeliveryChannel.SMS },
                            label = { Text("SMS") }
                        )
                        FilterChip(
                            selected = deliveryChannel == DeliveryChannel.WHATSAPP,
                            onClick = { deliveryChannel = DeliveryChannel.WHATSAPP },
                            label = { Text("WhatsApp") }
                        )
                    }
                    OutlinedTextField(
                        value = backendUrlText,
                        onValueChange = { backendUrlText = it.take(300) },
                        label = { Text("URL HTTPS del backend") },
                        placeholder = { Text("https://.../functions/v1/send-alert") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deviceTokenText,
                        onValueChange = { deviceTokenText = it.take(200) },
                        label = { Text("Token del dispositivo") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enviar alertas automáticamente")
                            Text(
                                "Se envía una vez por cada episodio nuevo de FC o SpO₂ bajo.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = deliveryEnabled, onCheckedChange = { deliveryEnabled = it })
                    }
                    Button(
                        onClick = {
                            deliveryTestStatus = "Enviando alerta de prueba..."
                            scope.launch {
                                onSendTestAlert(alertSettings).fold(
                                    onSuccess = { deliveryTestStatus = "Alerta de prueba enviada por ${deliveryChannelLabel(deliveryChannel)}." },
                                    onFailure = { deliveryTestStatus = "Error de envío: ${it.message ?: it::class.simpleName}" }
                                )
                            }
                        },
                        enabled = deliveryEnabled && phoneNumberText.isNotBlank() &&
                            backendUrlText.startsWith("https://") && deviceTokenText.isNotBlank()
                    ) {
                        Text("Enviar alerta de prueba")
                    }
                    deliveryTestStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    monitorSnapshot.lastDeliveryStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "SMS y WhatsApp dependen del proveedor configurado en el backend. Las credenciales del proveedor nunca se guardan en el APK.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Monitoreo nocturno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (monitorSnapshot.enabled) "● Activo" else "○ Inactivo")
                    monitorSnapshot.lastCheckAt?.let { Text("Último chequeo: ${timeFormatter.format(it)}") }
                    monitorSnapshot.lastBpm?.let { Text("FC en segundo plano: $it BPM") }
                    monitorSnapshot.lastOxygenPercent?.let { Text("SpO₂ en segundo plano: ${formatPercent(it)}%") }
                    monitorSnapshot.lastSource?.let { Text("Fuente FC: $it") }
                    monitorSnapshot.lastOxygenSource?.let { Text("Fuente SpO₂: $it") }
                    if (monitorSnapshot.heartAlertActive) {
                        Text("⚠ Alerta de FC activa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    if (monitorSnapshot.oxygenAlertActive) {
                        Text("⚠ Alerta de SpO₂ activa", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    monitorSnapshot.lastError?.let {
                        Text("Error de monitoreo: $it", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Mientras esté activo, Health Guard revisará Health Connect aproximadamente una vez por minuto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = if (monitorSnapshot.enabled) onStopMonitoring else onStartMonitoring,
                        enabled = hasBackgroundPermission && (hasHeartRatePermission || hasOxygenPermission)
                    ) {
                        Text(if (monitorSnapshot.enabled) "Detener monitoreo" else "Iniciar monitoreo nocturno")
                    }
                }
            }

            Text("Regla de frecuencia cardíaca", style = MaterialTheme.typography.titleLarge)
            Text("Valores de prueba configurables; no constituyen una recomendación médica.")
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
            Button(onClick = { heartSimulationEnabled = !heartSimulationEnabled }) {
                Text(if (heartSimulationEnabled) "Salir de simulación FC" else "Simular FC baja por 12 min")
            }
            AlertCard(
                title = "Resultado FC",
                active = heartAlert != null,
                lines = heartAlert?.let {
                    listOf(
                        "Mínima: ${it.minimumBpm} BPM",
                        "Última: ${it.latestBpm} BPM",
                        "Duración: ${it.durationMinutes} min",
                        "Lecturas consecutivas: ${it.readingsCount}"
                    )
                } ?: emptyList()
            )

            Text("Regla de SpO₂", style = MaterialTheme.typography.titleLarge)
            Text("La pulsera no es un dispositivo médico; la regla solo sirve para probar alertas con datos disponibles en Health Connect.")
            OutlinedTextField(
                value = oxygenThresholdText,
                onValueChange = { oxygenThresholdText = it.filter(Char::isDigit).take(3) },
                label = { Text("SpO₂ mínima (%)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = oxygenDurationText,
                onValueChange = { oxygenDurationText = it.filter(Char::isDigit).take(3) },
                label = { Text("Duración mínima SpO₂ (minutos)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = oxygenMinimumReadingsText,
                onValueChange = { oxygenMinimumReadingsText = it.filter(Char::isDigit).take(2) },
                label = { Text("Lecturas mínimas SpO₂") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { oxygenSimulationEnabled = !oxygenSimulationEnabled }) {
                Text(if (oxygenSimulationEnabled) "Salir de simulación SpO₂" else "Simular SpO₂ baja por 12 min")
            }
            AlertCard(
                title = "Resultado SpO₂",
                active = oxygenAlert != null,
                lines = oxygenAlert?.let {
                    listOf(
                        "Mínima: ${formatPercent(it.minimumPercent)}%",
                        "Última: ${formatPercent(it.latestPercent)}%",
                        "Duración: ${it.durationMinutes} min",
                        "Lecturas consecutivas: ${it.readingsCount}"
                    )
                } ?: emptyList()
            )

            Text("Últimas lecturas de FC", style = MaterialTheme.typography.titleLarge)
            if (displayedHeartReadings.isEmpty()) {
                Text("Todavía no hay lecturas de frecuencia cardíaca.")
            } else {
                displayedHeartReadings.sortedByDescending { it.timestamp }.take(10).forEach { reading ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(timeFormatter.format(reading.timestamp))
                        Text("${reading.bpm} BPM")
                    }
                }
            }

            Text("Últimas lecturas de SpO₂", style = MaterialTheme.typography.titleLarge)
            if (displayedOxygenReadings.isEmpty()) {
                Text("Sin datos de SpO₂ en Health Connect. Revisa que Mi Fitness tenga seguimiento de oxígeno activo y sincronizado.")
            } else {
                displayedOxygenReadings.sortedByDescending { it.timestamp }.take(10).forEach { reading ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(timeFormatter.format(reading.timestamp))
                        Text("${formatPercent(reading.percentage)}%")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Health Guard es una POC de alerta y acompañamiento. No diagnostica enfermedades ni sustituye dispositivos médicos o atención profesional.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusCard(
    healthConnectStatus: String,
    hasHeartRatePermission: Boolean,
    hasOxygenPermission: Boolean,
    hasBackgroundPermission: Boolean,
    backgroundFeatureAvailable: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Estado", style = MaterialTheme.typography.titleMedium)
            Text("Health Connect: $healthConnectStatus")
            Text("Lectura de FC: ${if (hasHeartRatePermission) "autorizada" else "sin autorización"}")
            Text("Lectura de SpO₂: ${if (hasOxygenPermission) "autorizada" else "sin autorización"}")
            Text(
                if (!backgroundFeatureAvailable) {
                    "Lectura en segundo plano: no disponible"
                } else {
                    "Lectura en segundo plano: ${if (hasBackgroundPermission) "autorizada" else "sin autorización"}"
                }
            )
        }
    }
}

@Composable
private fun VitalCard(
    title: String,
    value: String,
    timestamp: Instant?,
    source: String?,
    timeFormatter: DateTimeFormatter
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            timestamp?.let { Text("Última lectura: ${timeFormatter.format(it)}") }
            source?.let { Text("Fuente: $it") }
        }
    }
}

@Composable
private fun AlertCard(title: String, active: Boolean, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (active) {
                Text("⚠ Alerta detectada", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                lines.forEach { Text(it) }
            } else {
                Text("Sin alerta activa")
            }
        }
    }
}

private fun simulatedLowHeartReadings(now: Instant = Instant.now()): List<HeartRateReading> {
    val bpm = listOf(42, 41, 40, 39, 41, 40, 42)
    return bpm.mapIndexed { index, value ->
        HeartRateReading(
            timestamp = now.minusSeconds(((bpm.lastIndex - index) * 120).toLong()),
            bpm = value,
            source = "SIMULACIÓN"
        )
    }
}

private fun simulatedLowOxygenReadings(now: Instant = Instant.now()): List<OxygenSaturationReading> {
    val values = listOf(89.0, 88.0, 87.0, 88.0, 89.0, 88.0, 89.0)
    return values.mapIndexed { index, value ->
        OxygenSaturationReading(
            timestamp = now.minusSeconds(((values.lastIndex - index) * 120).toLong()),
            percentage = value,
            source = "SIMULACIÓN"
        )
    }
}

private fun formatPercent(value: Double): String = String.format(Locale.US, "%.0f", value)

private fun deliveryChannelLabel(channel: DeliveryChannel): String = when (channel) {
    DeliveryChannel.SMS -> "SMS"
    DeliveryChannel.WHATSAPP -> "WhatsApp"
}
