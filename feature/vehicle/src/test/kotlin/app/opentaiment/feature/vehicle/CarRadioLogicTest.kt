package app.opentaiment.feature.vehicle

import app.opentaiment.feature.vehicle.data.ReminderLevel
import app.opentaiment.feature.vehicle.data.ServiceReminder
import app.opentaiment.feature.vehicle.data.ServiceReminders
import app.opentaiment.feature.vehicle.data.TrackCsv
import app.opentaiment.feature.vehicle.data.TrackPoint
import app.opentaiment.feature.vehicle.data.TrackRecorder
import app.opentaiment.feature.vehicle.data.TripExport
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.obd.ElmResponseBuffer
import app.opentaiment.feature.vehicle.obd.ObdPid
import app.opentaiment.feature.vehicle.source.ReconnectPolicy
import app.opentaiment.feature.vehicle.tpms.TpmsLimits
import app.opentaiment.feature.vehicle.tpms.TpmsParser
import app.opentaiment.feature.vehicle.tpms.TpmsProblem
import app.opentaiment.feature.vehicle.tpms.Wheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ElmResponseBufferTest {
    @Test
    fun `joins BLE chunks into one response at the prompt`() {
        val b = ElmResponseBuffer()
        b.append("41 0C".toByteArray())
        assertNull(b.poll())
        b.append(" 1A F8\r\r".toByteArray())
        b.append(">".toByteArray())
        assertEquals("41 0C 1A F8\r\r", b.poll())
        assertNull(b.poll())
    }

    @Test
    fun `two responses in one chunk come out in order`() {
        val b = ElmResponseBuffer()
        b.append("OK\r>41 0D 32\r>".toByteArray())
        assertEquals("OK\r", b.poll())
        assertEquals("41 0D 32\r", b.poll())
    }
}

class ReconnectPolicyTest {
    @Test
    fun `backs off then settles at 30 seconds`() {
        assertEquals(2_000, ReconnectPolicy.delayMs(0))
        assertEquals(8_000, ReconnectPolicy.delayMs(2))
        assertEquals(30_000, ReconnectPolicy.delayMs(4))
        assertEquals(30_000, ReconnectPolicy.delayMs(500))
    }
}

class NewPidsTest {
    @Test
    fun `odometer is four bytes in tenths of a km`() {
        // 0x0001E240 = 123456 -> 12345.6 km
        assertEquals(12_345.6, ObdPid.ODOMETER.decodeOrNull(intArrayOf(0x00, 0x01, 0xE2, 0x40))!!, 0.001)
        assertNull(ObdPid.ODOMETER.decodeOrNull(intArrayOf(0x00, 0x01)))
    }

    @Test
    fun `baro is kPa`() {
        assertEquals(99.0, ObdPid.BARO.decodeOrNull(intArrayOf(99))!!, 0.0)
    }
}

class TpmsParserTest {
    // Sensor 1 (0x80…), 230 000 Pa = 2.30 bar, 24.50 °C, battery 91 %, no alarm.
    private val sample = byteArrayOf(
        0x80.toByte(), 0xEA.toByte(), 0xCA.toByte(), 0x10, 0x8A.toByte(), 0x78,
        0x70, 0x82.toByte(), 0x03, 0x00, // 230000 LE
        0x92.toByte(), 0x09, 0x00, 0x00, // 2450 LE
        91, 0,
    )

    @Test
    fun `decodes pressure temperature battery and wheel number`() {
        val r = TpmsParser.parse(sample, nowMs = 1_000)!!
        assertEquals(2.30, r.pressureBar, 0.001)
        assertEquals(24.5, r.temperatureC, 0.001)
        assertEquals(91, r.batteryPct)
        assertEquals(1, r.factoryPosition)
        assertEquals("80:EA:CA:10:8A:78", r.sensorId)
        assertEquals(Wheel.FRONT_LEFT, TpmsParser.wheelFor(r, emptyMap()))
    }

    @Test
    fun `rejects short or nonsense data`() {
        assertNull(TpmsParser.parse(ByteArray(10), 0))
        val crazy = sample.copyOf().also { it[9] = 0x7F } // ~21 000 bar
        assertNull(TpmsParser.parse(crazy, 0))
    }

    @Test
    fun `flags low pressure and lost sensors`() {
        val r = TpmsParser.parse(sample, nowMs = 0)!!.copy(pressureBar = 1.6)
        assertEquals(listOf(TpmsProblem.LOW), TpmsParser.problems(r, TpmsLimits(minBar = 1.9), nowMs = 1_000))
        assertEquals(listOf(TpmsProblem.STALE), TpmsParser.problems(r, TpmsLimits(), nowMs = TpmsParser.STALE_MS + 1))
        // Sensor lying in the glovebox reads ~0 bar: not a flat tyre.
        assertTrue(TpmsParser.problems(r.copy(pressureBar = 0.0), TpmsLimits(), 1_000).isEmpty())
    }

    @Test
    fun `user assignment wins over the printed number`() {
        val r = TpmsParser.parse(sample, 0)!!
        assertEquals(Wheel.REAR_RIGHT, TpmsParser.wheelFor(r, mapOf(r.sensorId to Wheel.REAR_RIGHT)))
        // Another sensor already took front-left: the printed number doesn't steal it.
        assertNull(TpmsParser.wheelFor(r, mapOf("other" to Wheel.FRONT_LEFT)))
    }
}

class ServiceRemindersTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val oil = ServiceReminder("oil", "Olej", 15_000, 365, lastDoneKm = 100_000.0, lastDoneEpochDay = LocalDate.of(2026, 3, 1).toEpochDay())

    @Test
    fun `counts kilometres and days left`() {
        val s = ServiceReminders.status(oil, odometerKm = 110_000.0, today = today)
        assertEquals(5_000.0, s.kmLeft!!, 0.01)
        assertEquals(112L, s.daysLeft)
        assertEquals(ReminderLevel.OK, s.level)
    }

    @Test
    fun `soon and overdue`() {
        assertEquals(ReminderLevel.SOON, ServiceReminders.status(oil, 114_500.0, today).level)
        assertEquals(ReminderLevel.DUE, ServiceReminders.status(oil, 115_200.0, today).level)
        assertEquals(ReminderLevel.DUE, ServiceReminders.status(oil, 101_000.0, LocalDate.of(2027, 3, 2)).level)
    }

    @Test
    fun `odometer from own reading plus real trips since`() {
        val trip = TripRecord(2_000, 3_000, 42.0, 1, 1, 1.0, 6.0, 90.0, null, null, null, demo = false)
        val demo = trip.copy(startEpochMs = 2_500, demo = true)
        val old = trip.copy(startEpochMs = 500)
        assertEquals(100_042.0, ServiceReminders.odometer(null, 100_000.0, baseEpochMs = 1_000, trips = listOf(trip, demo, old))!!, 0.01)
        assertEquals(123_456.0, ServiceReminders.odometer(123_456.0, 100_000.0, 1_000, listOf(trip))!!, 0.01)
        assertNull(ServiceReminders.odometer(null, null, 0, listOf(trip)))
    }

    @Test
    fun `round trips through JSON and falls back to defaults`() {
        val list = listOf(oil, oil.copy(id = "x", intervalKm = null, lastDoneKm = null))
        assertEquals(list, ServiceReminders.decode(ServiceReminders.encode(list)))
        assertEquals(ServiceReminders.DEFAULTS, ServiceReminders.decode(null))
        assertEquals(ServiceReminders.DEFAULTS, ServiceReminders.decode("not json"))
    }
}

class TrackTest {
    @Test
    fun `keeps a point every 25 m and drops inaccurate fixes`() {
        val rec = TrackRecorder()
        // ~11 m per 0.0001° latitude
        (0 until 10).forEach { i -> rec.add(TrackPoint(i * 1_000L, 52.0 + i * 0.0001, 21.0, 40.0), accuracyM = 5.0) }
        rec.add(TrackPoint(20_000, 52.5, 21.0, 40.0), accuracyM = 300.0)
        val pts = rec.snapshot()
        assertEquals(4, pts.size) // 0, 3, 6, 9 (≈33 m apart)
        assertTrue(pts.all { it.lat < 52.01 })
    }

    @Test
    fun `csv round trip`() {
        val pts = listOf(TrackPoint(1, 52.229676, 21.012229, 50.0), TrackPoint(2, 52.23, 21.02, null))
        assertEquals(pts, TrackCsv.decode(TrackCsv.encode(pts)))
    }

    private val trip = TripRecord(
        startEpochMs = 1_700_000_000_000, endEpochMs = 1_700_000_900_000, distanceKm = 12.34, durationMs = 900_000,
        movingMs = 800_000, fuelUsedL = 0.9, fuelPricePerL = 6.5, maxSpeedKmh = 92.0, ecoScore = 81,
        best0to100Sec = null, quarterMileSec = null, demo = false,
    )

    @Test
    fun `trips CSV uses semicolons and decimal commas`() {
        val csv = TripExport.tripsCsv(listOf(trip), ZoneId.of("Europe/Warsaw")).lines()
        assertTrue(csv[0].startsWith("start;koniec;dystans_km"))
        assertEquals("2023-11-14 23:13;2023-11-14 23:28;12,34;15,0;0,90;7,3;5,85;6,50;56;92;81;;;nie", csv[1])
    }

    @Test
    fun `gpx has one trkpt per point`() {
        val gpx = TripExport.gpx(trip, listOf(TrackPoint(1_700_000_000_000, 52.1, 21.1, 36.0), TrackPoint(1_700_000_001_000, 52.2, 21.2, null)))
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("lat=\"52.100000\" lon=\"21.100000\""))
        assertTrue(gpx.contains("<speed>10.00</speed>"))
    }
}
