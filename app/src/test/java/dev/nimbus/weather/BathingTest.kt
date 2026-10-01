/*
 * Nimbus - app/src/test/java/dev/nimbus/weather/BathingTest.kt
 * Bathing waters: EEA sites, Berlin and Schleswig-Holstein samples, sea temperature, names.
 *
 *   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 *   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
 *   Written by Anthropic Claude Opus 5.5 - AI generated content.
 *
 *   Free software under the GNU General Public License, version 3 or later.
 *   There is no warranty, to the extent permitted by law. The full text is in
 *   LICENSES/GPL-3.0-or-later.txt.
 *
 * SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
 * SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.nimbus.weather

import dev.nimbus.weather.data.model.BathingCategory
import dev.nimbus.weather.data.model.BathingQuality
import dev.nimbus.weather.data.model.BathingStatus
import dev.nimbus.weather.data.remote.BathingSource
import dev.nimbus.weather.data.remote.JsonCodec
import dev.nimbus.weather.ui.main.bathingName
import dev.nimbus.weather.ui.main.inSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BathingTest {
    @Test fun eeaSitesWithDistanceKindAndQuality() {
        // Excerpt of the EEA bathing water map service (layer 0), 1 October 2026
        val json = JsonCodec.parseToJsonElement(
            """{"features":[
              {"attributes":{"bathingWaterIdentifier":"DEBB_PR_0003","bathingWaterName":"GORINSEE, SCHÖNWALDE, BADEWIESE AM CAMPINGPLATZ",
               "bwWaterCategory":"Lake","latitude":52.6864,"longitude":13.4742,"qualityStatus":"Excellent",
               "bwProfileLink":"https://badestellen.brandenburg.de/documents/23251903/23266001/003.pdf"}},
              {"attributes":{"bathingWaterIdentifier":"DENW_PR_0121","bathingWaterName":"HEIDWEIHER STRANDBAD AM HEIDWEIHER",
               "bwWaterCategory":"Lake","latitude":51.2423,"longitude":6.2369,"qualityStatus":"Not classified","bwProfileLink":null}},
              {"attributes":{"bathingWaterIdentifier":"DESH_PR_0190","bathingWaterName":"OSTS, KLEIN WAABS","bwWaterCategory":"Coastal",
               "latitude":54.53,"longitude":10.0,"qualityStatus":"Good"}},
              {"attributes":{"bathingWaterIdentifier":"DESH_PR_0352","bathingWaterName":"OSTS;KIEL;KIELLINIE","bwWaterCategory":"Coastal",
               "latitude":46.26523,"longitude":14.47346,"qualityStatus":"Excellent","countryCode":"EU"}}
            ]}""",
        )
        val sites = BathingSource.parseEea(json, 52.52, 13.40)
        assertEquals(3, sites.size)
        val gorin = sites.first()
        assertEquals(BathingCategory.LAKE, gorin.category)
        assertEquals(BathingQuality.EXCELLENT, gorin.quality)
        assertEquals(18.7, gorin.distanceKm, 0.5)
        assertTrue(gorin.profileLink!!.endsWith(".pdf"))
        assertEquals(BathingQuality.NOT_CLASSIFIED, sites[1].quality)
        assertNull(sites[1].profileLink)
        assertEquals(BathingCategory.COAST, sites[2].category)
    }

    @Test fun berlinLatestSamples() {
        // LAGeSo letzte.csv (header and two rows, 1 October 2026)
        val csv = """BadName;Bezirk;Profil;RSS_Name;Latitude;Longitude;ProfilLink;BadestelleLink;Dat;Sicht;Eco;Ente;Farbe;BSL;Algen;Wasserqualitaet;cb;Temp;PDFLink;PrognoseLink;Farb_ID;Bemerkung;Weitere_Hinweise;Wasserqualitaet_predict;Dat_predict;FARB_ID;Wasserqualitaet_lageso;p2_5;p50;p90;p95;p97_5;classification
Alter Hof;Steglitz-Zehlendorf;Unterhavel;Alter Hof / Havel;52,432705;13,142278;"""+"\"\"\"Unterhavel - Alter Hof\"\":/x.php\""+""";https://www.berlin.de/a.php;15.09.2026;1,1;<15;<15;gruen.jpg;B334;;1;<300;18,9;"""+"\"\"\"PDF\"\":https://data.lageso.de/lageso/baden/bad29.pdf\""+""";;1;keine;Blaualgenmassenentwicklungen im Hoch- und Spätsommer möglich;nicht zutreffend;;1;1;;;;;;
Bammelecke;Treptow-Köpenick;Dahme;Bammelecke;52,407498;13,622537;"x";https://www.berlin.de/b.php;08.09.2026;0,9;<15;<15;gelb.jpg;B225;;1;<300;21,2;"y";;1;Blaualgenmassenentwicklungen;Vermehrt Blaualgen vorhanden.;nicht zutreffend;;1;1;;;;;;"""
        val rows = BathingSource.parseBerlin(csv)
        assertEquals(2, rows.size)
        val alterHof = rows[0]
        assertEquals(52.432705, alterHof.lat, 1e-6)
        assertEquals(18.9, alterHof.waterTemp!!, 1e-9)
        assertEquals(1.1, alterHof.visibilityM!!, 1e-9)
        assertEquals(BathingStatus.OK, alterHof.status)
        assertFalse(alterHof.algae)                                // "… möglich" is a general remark
        val bammel = rows[1]
        assertEquals(BathingStatus.WARNING, bammel.status)
        assertTrue(bammel.algae)
        assertEquals("Blaualgenmassenentwicklungen. Vermehrt Blaualgen vorhanden.", bammel.notice)
    }

    @Test fun schleswigHolsteinLatestSamplePerSite() {
        // v_proben_odata.csv: "|" separated, no header (ISO-8859-1 in the original)
        val csv = """DESH_PR_0190|OSTS, KLEIN WAABS, GEMEINDEBADESTELLE|0190_1|900|behördliche Überwachung|Küstengewässer|Ostsee|180015000|07.07.2026|Routineprobe|15|15|18|22|2|
DESH_PR_0190|OSTS, KLEIN WAABS, GEMEINDEBADESTELLE|0190_1|900|behördliche Überwachung|Küstengewässer|Ostsee|180015115|04.08.2026|sonstige Einzelprobe|146|221|20|19|1|
DESH_PR_0185|OSTS, DAMP, HAUPTSTRAND|0185_1|900|behördliche Überwachung|Küstengewässer|Ostsee|180015116|04.08.2026|sonstige Einzelprobe|87|32|20|20|1|Blaualgen sichtbar
garbage line"""
        val map = BathingSource.parseSh(csv)
        assertEquals(2, map.size)
        val waabs = map.getValue("DESH_PR_0190")
        assertEquals(20.0, waabs.waterTemp!!, 1e-9)               // the later sample wins
        assertEquals(1.0, waabs.visibilityM!!, 1e-9)
        assertTrue(map.getValue("DESH_PR_0185").algae)
    }

    @Test fun seaTemperaturesInRequestOrder() {
        val json = JsonCodec.parseToJsonElement(
            """[{"current":{"time":1790848800,"sea_surface_temperature":16.3}},
                {"current":{"time":1790848800,"sea_surface_temperature":17.2}}]""",
        )
        val sst = BathingSource.parseSeaTemperatures(json, listOf("A", "B"))
        assertEquals(16.3, sst.getValue("A").first, 1e-9)
        assertEquals(17.2, sst.getValue("B").first, 1e-9)
        assertEquals(1_790_848_800_000L, sst.getValue("B").second)
    }

    @Test fun namesAndSeason() {
        assertEquals("Gorinsee, Schönwalde, Badewiese am Campingplatz", bathingName("GORINSEE, SCHÖNWALDE, BADEWIESE AM CAMPINGPLATZ"))
        assertEquals("Kiel, Kiellinie", bathingName("OSTS;KIEL;KIELLINIE"))
        assertEquals("Moenkeberg", bathingName("OSTS;MOENKEBERG"))
        assertTrue(inSeason(LocalDate.of(2026, 7, 1)))
        assertTrue(inSeason(LocalDate.of(2026, 9, 15)))
        assertFalse(inSeason(LocalDate.of(2026, 10, 1)))
        assertFalse(inSeason(LocalDate.of(2026, 5, 14)))
    }
}
