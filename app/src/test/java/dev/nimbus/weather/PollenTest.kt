package dev.nimbus.weather

import dev.nimbus.weather.data.model.PollenForecast
import dev.nimbus.weather.data.model.PollenSourceKind
import dev.nimbus.weather.data.model.PollenType
import dev.nimbus.weather.data.remote.PollenSource
import dev.nimbus.weather.ui.main.composition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PollenTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `DWD level strings`() {
        assertEquals(0f, PollenSource.level("0"))
        assertEquals(0.5f, PollenSource.level("0-1"))
        assertEquals(2.5f, PollenSource.level("2-3"))
        assertNull(PollenSource.level("-1"))
    }

    @Test
    fun `region lookup result`() {
        val r = PollenSource.parseRegion(Fixtures.json("dwd_pollen_region.json"))!!
        assertEquals(12, r.first)
        assertTrue(r.second.startsWith("Geest"))
    }

    @Test
    fun `DWD index for a partregion, days keyed by issue date`() {
        val days = PollenSource.parseDwd(Fixtures.json("dwd_pollen.json"), 12)!!
        assertEquals(3, days.size)
        assertEquals(LocalDate.of(2026, 9, 28).atStartOfDay(berlin).toInstant().toEpochMilli(), days[0].date)
        assertEquals(8, days[0].levels.size)
        assertEquals(0.5f, days[0].levels[PollenType.GRASS])       // "0-1" in the fixture
        assertEquals(1f, days[1].levels[PollenType.GRASS])
    }

    @Test
    fun `regions without partregions match by region id`() {
        val days = PollenSource.parseDwd(Fixtures.json("dwd_pollen.json"), 20)   // Mecklenburg-Vorpommern
        assertNotNull(days)
        assertNull(PollenSource.parseDwd(Fixtures.json("dwd_pollen.json"), 999))
    }

    @Test
    fun `CAMS hourly is trimmed and turned into daily levels`() {
        val c = PollenSource.parseCams(Fixtures.json("cams_pollen.json"))!!
        assertTrue(c.times.size in 60..80)                          // ~3 days of data
        assertEquals(c.times.size, c.values.getValue(PollenType.MUGWORT).size)
        val days = PollenSource.camsDays(c.times, c.values, c.zone)
        assertTrue(days.size in 2..3)
        assertTrue(days.all { it.levels.containsKey(PollenType.GRASS) })
    }

    @Test
    fun `concentration thresholds differ by type`() {
        assertEquals(0f, PollenSource.levelFromConcentration(PollenType.BIRCH, 0.5))
        assertEquals(1f, PollenSource.levelFromConcentration(PollenType.BIRCH, 8.0))
        assertEquals(2f, PollenSource.levelFromConcentration(PollenType.GRASS, 40.0))
        assertEquals(3f, PollenSource.levelFromConcentration(PollenType.RAGWEED, 40.0))
    }

    @Test
    fun `composition shares over the next day`() {
        val now = 1_000_000_000_000L
        val times = (0 until 30).map { now + it * 3_600_000L }
        val p = PollenForecast(
            PollenSourceKind.CAMS, null, emptyList(), times,
            mapOf(PollenType.GRASS to times.map { 3.0 }, PollenType.MUGWORT to times.map { 1.0 }, PollenType.BIRCH to times.map { 0.0 }),
        )
        val c = composition(p, now)!!
        assertEquals(listOf(PollenType.GRASS, PollenType.MUGWORT), c.keys.toList())
        assertEquals(0.75, c.getValue(PollenType.GRASS) / c.values.sum(), 1e-9)
        val none = p.copy(hourly = mapOf(PollenType.GRASS to times.map { 0.1 }))
        assertNull(composition(none, now))
    }
}
