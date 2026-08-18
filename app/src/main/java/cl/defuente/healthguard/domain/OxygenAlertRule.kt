package cl.defuente.healthguard.domain

data class OxygenAlertRule(
    val lowOxygenThresholdPercent: Double = 90.0,
    val minimumDurationMinutes: Long = 5,
    val minimumReadings: Int = 2,
    val maximumGapBetweenReadingsMinutes: Long = 10,
    val maximumLatestReadingAgeMinutes: Long = 15
)

data class OxygenAlertEvent(
    val minimumPercent: Double,
    val latestPercent: Double,
    val durationMinutes: Long,
    val readingsCount: Int
)
