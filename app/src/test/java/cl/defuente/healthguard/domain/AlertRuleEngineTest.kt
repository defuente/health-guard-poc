package cl.defuente.healthguard.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AlertRuleEngineTest {
    private val rule = AlertRule(
        lowHeartRateThresholdBpm = 45,
        minimumDurationMinutes = 10,
        minimumReadings = 5
    )

    @Test
    fun `triggers alert when low readings persist long enough`() {
        val start = Instant.parse("2026-08-17T03:00:00Z")
        val readings = (0..6).map { index ->
            HeartRateReading(
                timestamp = start.plusSeconds(index * 120L),
                bpm = 40 + (index % 3),
                source = "test"
            )
        }

        val result = AlertRuleEngine.evaluate(readings, rule)

        assertNotNull(result)
        assertEquals(40, result?.minimumBpm)
        assertEquals(12L, result?.durationMinutes)
        assertEquals(7, result?.readingsCount)
    }

    @Test
    fun `does not trigger on one isolated low reading`() {
        val start = Instant.parse("2026-08-17T03:00:00Z")
        val readings = listOf(
            HeartRateReading(start, 70, "test"),
            HeartRateReading(start.plusSeconds(60), 39, "test"),
            HeartRateReading(start.plusSeconds(120), 68, "test")
        )

        assertNull(AlertRuleEngine.evaluate(readings, rule))
    }

    @Test
    fun `normal reading resets consecutive low sequence`() {
        val start = Instant.parse("2026-08-17T03:00:00Z")
        val readings = listOf(
            HeartRateReading(start, 40, "test"),
            HeartRateReading(start.plusSeconds(180), 41, "test"),
            HeartRateReading(start.plusSeconds(360), 42, "test"),
            HeartRateReading(start.plusSeconds(540), 70, "test"),
            HeartRateReading(start.plusSeconds(600), 40, "test"),
            HeartRateReading(start.plusSeconds(660), 41, "test")
        )

        assertNull(AlertRuleEngine.evaluate(readings, rule))
    }
}
