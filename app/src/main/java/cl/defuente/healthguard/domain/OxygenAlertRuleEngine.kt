package cl.defuente.healthguard.domain

import java.time.Duration
import java.time.Instant

object OxygenAlertRuleEngine {
    fun evaluate(
        readings: List<OxygenSaturationReading>,
        rule: OxygenAlertRule,
        now: Instant = Instant.now()
    ): OxygenAlertEvent? {
        val ordered = readings
            .filter { it.percentage in 1.0..100.0 }
            .sortedBy { it.timestamp }

        if (ordered.isEmpty()) return null

        val latestAgeMinutes = Duration.between(ordered.last().timestamp, now).toMinutes()
        if (latestAgeMinutes > rule.maximumLatestReadingAgeMinutes) return null

        val trailingLowReadings = mutableListOf<OxygenSaturationReading>()
        var previousLow: OxygenSaturationReading? = null

        for (reading in ordered) {
            if (reading.percentage < rule.lowOxygenThresholdPercent) {
                val previous = previousLow
                if (previous != null) {
                    val gap = Duration.between(previous.timestamp, reading.timestamp).toMinutes()
                    if (gap > rule.maximumGapBetweenReadingsMinutes) {
                        trailingLowReadings.clear()
                    }
                }
                trailingLowReadings += reading
                previousLow = reading
            } else {
                trailingLowReadings.clear()
                previousLow = null
            }
        }

        if (trailingLowReadings.size < rule.minimumReadings) return null

        val duration = Duration.between(
            trailingLowReadings.first().timestamp,
            trailingLowReadings.last().timestamp
        )
        if (duration.toMinutes() < rule.minimumDurationMinutes) return null

        return OxygenAlertEvent(
            minimumPercent = trailingLowReadings.minOf { it.percentage },
            latestPercent = trailingLowReadings.last().percentage,
            durationMinutes = duration.toMinutes(),
            readingsCount = trailingLowReadings.size
        )
    }
}
