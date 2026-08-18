package cl.defuente.healthguard.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.permission.HealthPermission.Companion.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cl.defuente.healthguard.domain.HeartRateReading
import cl.defuente.healthguard.domain.OxygenSaturationReading
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectRepository(context: Context) {
    private val appContext = context.applicationContext
    private val heartRateReadPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val oxygenSaturationReadPermission = HealthPermission.getReadPermission(OxygenSaturationRecord::class)

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun statusLabel(): String = when (sdkStatus()) {
        HealthConnectClient.SDK_AVAILABLE -> "Disponible"
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Requiere instalar o actualizar Health Connect"
        else -> "No disponible en este dispositivo"
    }

    fun isBackgroundReadFeatureAvailable(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            HealthConnectClient.getOrCreate(appContext)
                .features
                .getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
    }

    fun permissionsToRequest(): Set<String> = buildSet {
        add(heartRateReadPermission)
        add(oxygenSaturationReadPermission)
        if (isBackgroundReadFeatureAvailable()) {
            add(PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
    }

    suspend fun hasHeartRateReadPermission(): Boolean = hasPermission(heartRateReadPermission)

    suspend fun hasOxygenSaturationReadPermission(): Boolean = hasPermission(oxygenSaturationReadPermission)

    suspend fun hasBackgroundReadPermission(): Boolean {
        if (!isAvailable() || !isBackgroundReadFeatureAvailable()) return false
        val granted = HealthConnectClient.getOrCreate(appContext)
            .permissionController
            .getGrantedPermissions()
        return PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted
    }

    private suspend fun hasPermission(permission: String): Boolean {
        if (!isAvailable()) return false
        val granted = HealthConnectClient.getOrCreate(appContext)
            .permissionController
            .getGrantedPermissions()
        return permission in granted
    }

    suspend fun readRecentHeartRate(hours: Long = 12): List<HeartRateReading> {
        check(isAvailable()) { "Health Connect no está disponible" }
        check(hasHeartRateReadPermission()) { "Falta permiso para leer frecuencia cardíaca" }

        val end = Instant.now()
        val start = end.minus(hours, ChronoUnit.HOURS)
        val client = HealthConnectClient.getOrCreate(appContext)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1000
            )
        )

        return response.records
            .flatMap { record ->
                val source = record.metadata.dataOrigin.packageName
                record.samples.map { sample ->
                    HeartRateReading(
                        timestamp = sample.time,
                        bpm = sample.beatsPerMinute.toInt(),
                        source = source
                    )
                }
            }
            .sortedByDescending { it.timestamp }
    }

    suspend fun readRecentOxygenSaturation(hours: Long = 12): List<OxygenSaturationReading> {
        check(isAvailable()) { "Health Connect no está disponible" }
        check(hasOxygenSaturationReadPermission()) { "Falta permiso para leer saturación de oxígeno" }

        val end = Instant.now()
        val start = end.minus(hours, ChronoUnit.HOURS)
        val client = HealthConnectClient.getOrCreate(appContext)
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1000
            )
        )

        return response.records
            .map { record ->
                OxygenSaturationReading(
                    timestamp = record.time,
                    percentage = record.percentage.value,
                    source = record.metadata.dataOrigin.packageName
                )
            }
            .sortedByDescending { it.timestamp }
    }
}
