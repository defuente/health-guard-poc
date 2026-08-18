package cl.defuente.healthguard.domain

import java.time.Instant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OxygenAlertRuleEngineTest {
    private val rule = OxygenAlertRule(
        lowOxygenThresholdPercent = 90.0,
        minimumDurationMinutes = 5,
        minimumReadings = 3,
        maximumGapBetweenReadingsMinutes = 5,
        maximumLatestReadingAgeMinutes = 15
    )

    @Test
    fun `triggers oxygen alert for sustained recent low readings`() {
        val now = Instant.parse("2026-08-18T03:30:00Z")
        val readings = listOf(
            OxygenSaturationReading(now.minusSeconds(8 * 60), 89.0, "test"),
            OxygenSaturationReading(now.minusSeconds(5 * 60), 88.0, "test"),
            OxygenSaturationReading(now.minusSeconds(2 * 60), 87.0, "test")
        )

        assertNotNull(OxygenAlertRuleEngine.evaluate(readings, rule, now))
    }

    @Test
    fun `does not trigger when oxygen readings have a large gap`() {
        val now = Instant.parse("2026-08-18T03:30:00Z")
        val readings = listOf(
            OxygenSaturationReading(now.minusSeconds(20 * 60), 89.0, "test"),
            OxygenSaturationReading(now.minusSeconds(4 * 60), 88.0, "test"),
            OxygenSaturationReading(now.minusSeconds(2 * 60), 87.0, "test")
        )

        assertNull(OxygenAlertRuleEngine.evaluate(readings, rule, now))
    }

    @Test
    fun `does not trigger from stale oxygen readings`() {
        val now = Instant.parse("2026-08-18T03:30:00Z")
        val readings = listOf(
            OxygenSaturationReading(now.minusSeconds(40 * 60), 89.0, "test"),
            OxygenSaturationReading(now.minusSeconds(35 * 60), 88.0, "test"),
            OxygenSaturationReading(now.minusSeconds(30 * 60), 87.0, "test")
        )

        assertNull(OxygenAlertRuleEngine.evaluate(readings, rule, now))
    }
}
