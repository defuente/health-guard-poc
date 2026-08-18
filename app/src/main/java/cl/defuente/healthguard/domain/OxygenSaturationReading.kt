package cl.defuente.healthguard.domain

import java.time.Instant

data class OxygenSaturationReading(
    val timestamp: Instant,
    val percentage: Double,
    val source: String
)
