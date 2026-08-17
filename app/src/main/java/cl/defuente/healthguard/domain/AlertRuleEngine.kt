package cl.defuente.healthguard.domain

import java.time.Duration

object AlertRuleEngine {
    fun evaluate(readings: List<HeartRateReading>, rule: AlertRule): AlertEvent? {
        val ordered = readings
            .filter { it.bpm in 1..300 }
            .sortedBy { it.timestamp }

        if (ordered.isEmpty()) return null

        val trailingLowReadings = mutableListOf<HeartRateReading>()

        for (reading in ordered) {
            if (reading.bpm < rule.lowHeartRateThresholdBpm) {
                trailingLowReadings += reading
            } else {
                trailingLowReadings.clear()
            }
        }

        if (trailingLowReadings.size < rule.minimumReadings) return null

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
