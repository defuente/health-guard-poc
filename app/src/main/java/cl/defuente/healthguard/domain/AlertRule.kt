package cl.defuente.healthguard.domain

data class AlertRule(
    val lowHeartRateThresholdBpm: Int = 45,
    val minimumDurationMinutes: Long = 10,
    val minimumReadings: Int = 5,
    val maximumGapBetweenReadingsMinutes: Long = 5,
    val maximumLatestReadingAgeMinutes: Long = 10
)

data class AlertEvent(
    val minimumBpm: Int,
    val latestBpm: Int,
    val durationMinutes: Long,
    val readingsCount: Int
)
