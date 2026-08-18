package cl.defuente.healthguard.domain

import java.time.Duration
import java.time.Instant

object AlertRuleEngine {
    fun evaluate(
        readings: List<HeartRateReading>,
        rule: AlertRule,
        now: Instant = Instant.now()
    ): AlertEvent? {
        val ordered = readings
            .filter { it.bpm in 1..300 && !it.timestamp.isAfter(now.plusSeconds(60)) }
            .sortedBy { it.timestamp }

        if (ordered.isEmpty()) return null

        val trailingLowReadings = mutableListOf<HeartRateReading>()

        for (reading in ordered) {
            if (reading.bpm >= rule.lowHeartRateThresholdBpm) {
                trailingLowReadings.clear()
                continue
            }

            val previous = trailingLowReadings.lastOrNull()
            if (previous != null) {
                val gapMinutes = Duration.between(previous.timestamp, reading.timestamp).toMinutes()
                if (gapMinutes > rule.maximumGapBetweenReadingsMinutes) {
                    trailingLowReadings.clear()
                }
            }
            trailingLowReadings += reading
        }

        if (trailingLowReadings.size < rule.minimumReadings) return null

        val latestAge = Duration.between(trailingLowReadings.last().timestamp, now)
        if (latestAge.isNegative || latestAge.toMinutes() > rule.maximumLatestReadingAgeMinutes) {
            return null
        }

        val duration = Duration.between(
            trailingLowReadings.first().timestamp,
            trailingLowReadings.last().timestamp
        )

        if (duration.toMinutes() < rule.minimumDurationMinutes) return null

        return AlertEvent(
            minimumBpm = trailingLowReadings.minOf { it.bpm },
            latestBpm = trailingLowReadings.last().bpm,
            durationMinutes = duration.toMinutes(),
            readingsCount = trailingLowReadings.size
        )
    }
}
