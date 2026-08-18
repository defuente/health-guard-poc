package cl.defuente.healthguard.domain

import java.time.Instant

data class HeartRateReading(
    val timestamp: Instant,
    val bpm: Int,
    val source: String
)
