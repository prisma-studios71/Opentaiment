package app.opentaiment.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationJsonTest {
    private val response = """
        [
          {"stationuuid":"a1","name":" RMF FM ","url":"http://x/rmf","url_resolved":"https://rmf.example/stream.mp3",
           "favicon":"https://rmf.example/logo.png","countrycode":"PL","tags":"pop,hits, polish","bitrate":128,"codec":"MP3","lastcheckok":1},
          {"stationuuid":"b2","name":"Broken","url":"http://dead","url_resolved":"","lastcheckok":0},
          {"stationuuid":"","name":"No id","url_resolved":"http://y"},
          {"stationuuid":"c3","name":"Dup","url_resolved":"https://rmf.example/stream.mp3","lastcheckok":1},
          {"stationuuid":"d4","name":"Trójka","url":"https://trojka.example/hls.m3u8","favicon":"","bitrate":0,"codec":"AAC+"}
        ]
    """.trimIndent()

    @Test
    fun `parses radio browser and skips broken, id-less and duplicate streams`() {
        val list = StationJson.parseRadioBrowser(response)
        assertEquals(listOf("a1", "d4"), list.map { it.id })
        val rmf = list[0]
        assertEquals("RMF FM", rmf.name)
        assertEquals("https://rmf.example/stream.mp3", rmf.streamUrl)
        assertEquals(listOf("pop", "hits", "polish"), rmf.tags)
        assertEquals("MP3 · 128 kbps · pop, hits, polish", rmf.details)
        assertEquals("https://trojka.example/hls.m3u8", list[1].streamUrl) // falls back to url
        assertEquals(null, list[1].faviconUrl)
        assertEquals(null, list[1].bitrateKbps)
    }

    @Test
    fun `garbage gives an empty list`() {
        assertTrue(StationJson.parseRadioBrowser("<html>").isEmpty())
    }

    @Test
    fun `favourites round trip`() {
        val stations = StationJson.parseRadioBrowser(response) + RadioStation("custom-1", "Moja", "https://my/stream")
        assertEquals(stations, StationJson.decode(StationJson.encode(stations)))
        assertTrue(StationJson.decode(null).isEmpty())
    }

    @Test
    fun `local tracks sort by folder then title, Polish order`() {
        fun t(id: Long, title: String, folder: String) = LocalTrack(id, title, null, null, 0, "u$id", folder)
        val sorted = LocalMusic.sortTracks(listOf(t(1, "Żaba", "B"), t(2, "Ala", "B"), t(3, "Zorro", "A"), t(4, "Łódź", "B")))
        assertEquals(listOf(3L, 2L, 4L, 1L), sorted.map { it.id })
    }
}
