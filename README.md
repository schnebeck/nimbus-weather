<!--
  Nimbus - README.md
  Overview: features, data sources, building and licensing.

    Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
    Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
    Written by Anthropic Claude Opus 5.5 - AI generated content.

    Free software under the GNU General Public License, version 3 or later.
    There is no warranty, to the extent permitted by law. The full text is in
    LICENSES/GPL-3.0-or-later.txt.

  SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
  SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
  SPDX-License-Identifier: GPL-3.0-or-later
-->

<p align="right"><a href="README.en.md">English</a></p>

# Nimbus – Wetter für Deutschland und Europa

Nimbus symbolisiert das aktuelle Wetter mit passenden kleinen Hintergrundanimationen – ziehende Wolken,
Regen, Schnee oder ein Sternenhimmel. Die Daten kommen von den Wetterdiensten Europas – Messwerte der
nächsten Wetterstation (DWD, GeoSphere Austria, MeteoSwiss, DMI, sonst ein Flughafen), die Vorhersage
des feinsten Modells für den Ort oder eines Modells nach Wahl, auch je Ort, und das Regenradar von DWD
und KNMI. Keine Werbung, kein Konto, keine Google-Dienste.

<p align="center">
  <img src="docs/screenshots/scenes.png" width="820" alt="Animierte Wetterszenen: Sonne, Herbstlaub, Gewitter, Schnee, Regen, klare Nacht">
  <br><sub>Der Himmel folgt dem Wetter, dem Sonnenstand und der Jahreszeit (hier im Demo-Modus).</sub>
</p>

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/main.png" width="240" alt="Wetterseite"><br><sub><b>Wetterseite</b><br>Messwerte der nächsten Wetterstation, Kurzvorhersage, Stunden und 10 Tage</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/meteogram.png" width="240" alt="Meteogramm"><br><sub><b>Meteogramm</b><br>Temperatur, Regen, Sonnenschein und Wind je Stunde – mit Schieber</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/history.png" width="240" alt="Rückblick"><br><sub><b>Rückblick</b><br>Was war gemessen, was war vorhergesagt? Einfach nach rechts wischen</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/radar.png" width="240" alt="Regenradar"><br><sub><b>Regenradar</b><br>DWD-Radar mit Vorhersage, Temperatur- und Windebene</sub></td>
    <td align="center"><img src="docs/screenshots/sun.png" width="240" alt="Sonne und Mond"><br><sub><b>Sonne und Mond</b><br>Sonnenbogen mit Morgenrot und Blauer Stunde, Mondphase</sub></td>
    <td align="center" valign="middle">
      <b>Herunterladen</b><br><br>
      <a href="https://github.com/schnebeck/nimbus-weather/releases/latest">Neueste Version (APK)</a><br><br>
      <sub>Android 8.0 oder neuer<br>ohne Google Play Services</sub>
    </td>
  </tr>
</table>

## Installation

Die signierten APKs gibt es unter [Releases](https://github.com/schnebeck/nimbus-weather/releases/latest):

| Datei | Für |
|---|---|
| `Nimbus-<Version>-arm64.apk` | praktisch alle Android-Handys seit ca. 2017 |
| `Nimbus-<Version>-universal.apk` | alle Geräte inkl. 32-Bit-ARM und x86 |

APK aufs Handy kopieren, öffnen und die Installation aus unbekannten Quellen erlauben.
Voraussetzung: Android 8.0 (API 26). Google Play Services werden nicht benötigt.

## Funktionen

- **Animierter Himmel**: Farbverlauf nach Sonnenstand, Sonne, Mond in der echten Phase, Sterne, Wolken,
  Nebel, Regen, Schnee und Gewitter. Wind und Böen bewegen Wolken, Niederschlag und Partikel; bei
  trockenem Wetter treiben je nach Jahreszeit Blüten, Samen, Blätter oder Eiskristalle, Pollen nach
  der echten Belastung.
- **Wetterseite**: Kombisymbol, Temperatur mit Einheit und Max/Min, Kurzvorhersage für die nächsten
  Stunden, DWD-Warnungen, „Niederschlag heute“ obenauf (Menge, nächste 3 Stunden, höchste
  Wahrscheinlichkeit; an trockenen Tagen eine Zeile mit dem nächsten Niederschlag oder ausgeblendet),
  Stunden- und 10-Tage-Vorhersage mit Niederschlagswahrscheinlichkeit, Kacheln für Gefühlt, UV, Wind,
  Luftfeuchte, Sichtweite und Luftdruck. Die Vorhersage erscheint sofort, Kacheln mit langsameren
  Quellen kommen hinzu, sobald ihre Daten da sind; ein Punkt je Kachel zeigt, ob ihre Daten aktuell
  sind (grün) oder noch die vorigen (gelb). Jede Datenart hat eine Haltbarkeit (Vorhersage 10, Rückblick
  15 Minuten): zurückgeholt und danach minütlich lädt die App nach, was abgelaufen ist.
  Aktuelle Werte stammen, wo möglich, von der nächsten Wetterstation: DWD, GeoSphere Austria,
  MeteoSwiss und DMI alle 10 Minuten, sonst ein Flughafen (METAR). Jeder Wert zählt nur von einer
  Station in der Höhe des Ortes (höchstens 300 m Unterschied) und nahe am Modell. Der Himmel „jetzt“
  folgt dem gemessenen Sonnenschein, wie die Stunden dem Sonnenschein des Modells – dünne Schleierwolken
  machen keinen bewölkten Himmel, wenn die Sonne durchscheint; die laufende Stunde im Tagesdiagramm
  zeigt dasselbe Wetter wie die Kopfzeile. ⓘ am Hinweis „Gemessen an …“ nennt für jeden Wert, ob er
  gemessen ist (und wo) oder vom Modell kommt. Netze und Regeln: [docs/STATIONS.md](docs/STATIONS.md).
- **Meteogramm** je Tag (00–24 Uhr): Temperatur (Vorhersage alle 15 Minuten, Messwerte der
  DWD-Station alle 10 Minuten), Niederschlag, Sonnenscheindauer und Wind pro Stunde,
  Nachtschattierung, Legende mit Tagessummen. Die 10-Minuten-Messwerte der Station als
  30-Minuten-Mittel, die Vorhersage schließt ohne Sprung an die letzte Messung an. Niederschlag wahlweise im Temperaturdiagramm oder als
  eigenes Diagramm darunter, mit der Wahrscheinlichkeit als Linie (auch im Rückblick). Langes Drücken
  blendet einen Cursor mit allen Werten der Stunde ein. Heute zeigen Meteogramm, Niederschlag und Luftdruck für die vergangenen Stunden
  die Messwerte der DWD-Station, danach die Vorhersage.
- **Rückblick** per Wischen nach rechts: heute bisher, gestern, vorgestern – DWD-Messwerte im
  Vergleich zur Vorhersage des gewählten Modells, oben der Tag in Abschnitten (früh bis nachts)
  mit Symbol und Wetter, der Himmel zeigt sie nacheinander; mit mittlerer Abweichung, Stundenwerte als Tabelle
  Messung | Vorhersage; Niederschlag gemessen und vorhergesagt mit Wahrscheinlichkeit; dazu das
  Regenradar des ganzen Tages in 5-Minuten-Schritten zum Abspielen, Verschieben und Zoomen
  (wo ein Wetterdienst-Radar misst: DWD, KNMI, MET Norway).
- **Sonne und Mond**: Sonnenbogen mit fester Skala je Ort (die Bogenhöhe zeigt die Jahreszeit),
  Lichtphasen Tag, Morgen-/Abendrot, Blaue Stunde und Nacht, Tageslänge; Mondphase, Auf- und Untergang.
- **Umwelt**: Luftqualität, Pollenflug (DWD-Index und Zusammensetzung, Arten wählbar – z. B. nur
  Bäume), Bürger-Messnetz, Vergleich von acht Wettermodellen.
- **Gezeiten und Pegel**: Die Pegel im Umkreis von 10 km, je Gewässer einer (z. B. Weser, Fulda
  und Werra in Hann. Münden), freie Plätze mit weiteren Gewässern bis 15 km. An der Küste und an Tideflüssen zuerst die nächsten Hoch- und
  Niedrigwasser mit Tidekurve – selbst berechnet aus vier Wochen Pegelmessungen, etwa ±30 Minuten
  genau, nicht für Navigation oder Wattwanderungen. Messwerte von den Bundeswasserstraßen und aus
  Niedersachsen, NRW, Sachsen und Hessen, sonst die Hochwasser-Einstufung des Länderportals mit
  Link; dazu die Hochwasserwarnungen der Länder. Welche Quelle je Bundesland genutzt wird und
  warum: [docs/GAUGES.md](docs/GAUGES.md).
- **Badegewässer**: alle amtlichen EU-Badestellen im einstellbaren Umkreis (10–100 km) – Seen,
  Flüsse, Küsten –, Favoriten in jeder Entfernung; EU-Einstufung, Meerestemperatur an der Küste,
  in Berlin und Schleswig-Holstein die letzten Proben mit Wassertemperatur und Blaualgen-Hinweisen.
  Quellen je Bundesland: [docs/BATHING.md](docs/BATHING.md).
- **Regenradar**: DWD-Radar mit 2-h-Vorhersage für Deutschland, KNMI-Radar für die Niederlande,
  MET-Norway-Radar für Norwegen, Schweden, Finnland und Dänemark (1 km, herausgezoomt als Übersicht in
  gröberen Zellen), RainViewer für das übrige Europa (alle 10 Minuten, die Schritte dazwischen aus der
  Bewegung berechnet; die Zeitachse richtet sich nach dem Radar des Ortes), einheitliche
  Farbskala für Regen (grün → gelb → rot → magenta) und Schnee (türkis → weiß → violett, pro Pixel
  nach der Temperatur), wahlweise ruhiger in Blau bzw. Rosa–Violett; die Radarzellen in jeder
  Zoomstufe geglättet statt als Blöcke; flüssiges Abspielen: zwischen zwei Radarbildern wird die
  Bewegung der Regengebiete berechnet; eigener Radarspeicher – jedes Bild eines Wetterdienstes (ganz
  Deutschland, die Niederlande in einem) wird einmal geladen, zu Reflektivität aufbereitet und bis zum Verfall lokal gehalten
  (Analysen ~3½ Tage), Verschieben und Zoomen brauchen kein Netz; deckende Farben, Straßen, Grenzen
  und Namen über dem Radar; Temperatur- (mit Isothermen) und Windebene, Satellit (Meteosat, alle
  10 Minuten, zur Zeit des Radarbilds), Warnkarte, Rückblick bis 24 h. Auch das Radar eines Tages
  im Rückblick zeigt Temperatur, Wind und Satellit. Im WLAN hält die App die 2-Stunden-Schleife des aktuellen Orts etwa alle 15
  Minuten aktuell, auch im Hintergrund – solange die App am Vortag benutzt wurde. Die Auflösung des
  Radarbilds richtet sich nach dem Speicher des Geräts. Ohne Verbindung zeigt das Radar die
  gespeicherten Bilder; nichts wartet endlos. Die Niederschlagskarte auf der Wetterseite zeigt dasselbe
  Bild für ihren Ausschnitt um den Ort; sie lädt nur dessen Radarzellen, in eigener Spur neben der
  Radarschleife.
- **Akku**: Der animierte Himmel läuft mit 30 Bildern/s (Regen, Schnee: 60), nach einer Minute ohne
  Berührung mit 15; im Energiesparmodus des Systems steht er still. Die stündliche Aktualisierung im
  Hintergrund lädt nur die Vorhersage (die übrigen Quellen beim Öffnen) und pausiert, wenn die App
  drei Tage nicht geöffnet wurde.
- **Barrierefreiheit**: große Systemschrift bis 200 % und kleine Displays ab 320 dp – nichts überlappt
  oder wird abgeschnitten, lange Wörter werden getrennt oder abgekürzt; die 10-Tage-Zeilen werden
  bei großer Schrift zweizeilig.
- **Vollbild**: Doppeltippen auf den Himmel über den Karten blendet Status- und Navigationsleiste aus
  und wieder ein, optional auch per Knopf; mit Drei-Tasten-Navigation endet die App über den Tasten.
- **Lexikon**: ⓘ an jeder Karte erklärt die Begriffe.
- **Mein Standort**: Solange er zu sehen ist (seine Seite, die Ortsliste), sucht die App die Position
  beim Öffnen und nach 5 Minuten neu – zuerst per Mobilfunk/WLAN, gibt das nichts (etwa im EDGE-Netz),
  zusätzlich per GPS. Ein Punkt am Standort-Pin zeigt, ob die Position aktuell ist (grün) oder älter
  (gelb, dann auch die Kacheln); bleibt die Suche erfolglos, wartet die nächste länger (2, 5, 10 Minuten).
  Bei gespeicherten Orten bleibt das GPS aus. Neu laden (oder Tipp auf den Pin) fragt erst die
  Position wirklich neu ab – alles für „Mein Standort“ ist gelb und wartet, bis sie bestätigt oder der
  neue Ort übernommen ist; dann werden die Kacheln nacheinander grün. Neu laden holt alles frisch
  (ohne Cache, auch Stationslisten und Gezeiten); scheitert ein Abruf, stehen die Altdaten gelb da
  und werden beim nächsten Mal erneut versucht.
- **Haltbarkeit**: Jeder Teil der Daten hat seine eigene: Vorhersage, Station, Bürgersensoren und
  Hochwassermeldungen 10 Minuten, Pegel 15, Luftqualität und Badestellen 60, Pollen 3 Stunden. Ist er
  abgelaufen, meldet er das selbst – sein Punkt wird im selben Moment gelb (kein Zeitgeber, der die
  Seite neu zeichnet) – und nur er wird neu geladen; jede Kachel wird grün, sobald ihre Quelle
  geantwortet hat. Ist der Standort veraltet, sind es alle Daten von „Mein Standort“ mit ihm. Gespeichertes ohne Nutzen wird gelöscht: Momentdaten nach 4 Tagen (so weit reicht
  der Rückblick), Listen (Pegel, Badestellen, Gezeiten) nach 30 Tagen ohne Nutzung, das Wetter
  entfernter Orte sofort.
- **Orte, Einstellungen**: Standort und gespeicherte Orte (lange drücken zum Sortieren und Löschen);
  Vorhersagemodell (voreingestellt: Automatisch – das feinste Modell je Ort und Zeitraum, die
  Datenquellen nennen es mit seiner Auflösung), Einheiten (beim ersten Start passend zum Land),
  Stationswerte, Animationen, Reihenfolge und Sichtbarkeit der Kacheln.
- **Modell je Ort**: In der Ortsliste (lange drücken) wählt man für jeden Ort zuerst „Wie in den
  App-Einstellungen“ oder „Eigenes Modell für diesen Ort“, dann das Modell – auch regionale wie MET
  Nordic (1 km) oder KNMI Harmonie (2 km); außerhalb ihres Gebiets und nach ihren letzten Stunden
  übernimmt „Automatisch“. ⧉ legt einen Ort ein weiteres Mal an, etwa um zwei Modelle nebeneinander zu
  sehen. Alle Modelle mit Gitter, Gebiet und Reichweite: [docs/MODELS.md](docs/MODELS.md).
- **Tablet, Querformat**: ab 600 dp Breite die Kacheln in zwei Spalten (eine Karte, die allein in ihrer
  Zeile stünde, über die volle Breite), quer gehaltene Handys in drei gleich breiten Spalten: der Kopf links, die Kacheln in den zwei anderen
  (nichts unter der Kamera-Aussparung), im Querformat großer Tablets links eine
  Ortsleiste; Einstellungen, Orte, Rückblick und Radar-Bedienung mittig in lesbarer Breite. Unter „Open-Source-Lizenzen“ stehen die Lizenzen
  von Nimbus und aller verwendeten Bibliotheken. Stündliche Aktualisierung im Hintergrund,
  offline die zuletzt geladenen Daten.

## Datenquellen

| Zweck | Quelle |
|---|---|
| Vorhersage | [Open-Meteo](https://open-meteo.com): voreingestellt „best match“, wählbar DWD ICON, ECMWF IFS, Météo-France, MET Nordic (MET Norway), KNMI Harmonie, DMI Harmonie, UK Met Office, MeteoSwiss ICON-CH1/-CH2, GeoSphere AROME, ItaliaMeteo ICON-2I; Lücken aus „best match“ |
| Aktuelle Messwerte | DWD-Stationen via [Bright Sky](https://brightsky.dev), [GeoSphere Austria](https://data.hub.geosphere.at), [MeteoSwiss](https://opendatadocs.meteoswiss.ch), [DMI](https://www.dmi.dk/friedata), Flughäfen (METAR, [aviationweather.gov](https://aviationweather.gov)) |
| Warnungen, Rückblick | DWD-Warnungen und -Stationen via Bright Sky |
| Radar, Warnkarte | DWD GeoServer, [KNMI](https://english.knmidata.nl/open-data) (Niederlande), [MET Norway](https://www.met.no/en/free-meteorological-data) (Nordic-Komposit); übriges Europa: [RainViewer](https://www.rainviewer.com/api.html) |
| Satellit | Meteosat (MTG, GeoColour) via [EUMETView](https://view.eumetsat.int) – „Contains modified EUMETSAT Meteosat data“, CC BY 4.0 |
| Luftqualität, Pollen Europa | Copernicus CAMS via Open-Meteo |
| Pollenflug Deutschland | [DWD-Pollenflug-Gefahrenindex](https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json) |
| Bürger-Messnetz | [Sensor.Community](https://sensor.community) |
| Modellvergleich, Temperatur-/Windgitter | Open-Meteo |
| Pegel, Gezeiten, Hochwasser | PEGELONLINE (WSV), NLWKN, LANUK NRW, LfULG Sachsen, HLNUG, Länderübergreifendes Hochwasserportal – Details in [docs/GAUGES.md](docs/GAUGES.md) |
| Badegewässer | Europäische Umweltagentur, LAGeSo Berlin, Schleswig-Holstein, Meerestemperatur Open-Meteo – Details in [docs/BATHING.md](docs/BATHING.md) |
| Ortsname des Standorts | System-Geocoder, sonst [Nominatim](https://nominatim.org) (OpenStreetMap) |
| Sonne und Mond | auf dem Gerät berechnet (nach SunCalc und J. Meeus) |
| Karte | [OpenFreeMap](https://openfreemap.org) · © OpenMapTiles · © OpenStreetMap-Mitwirkende |

Die freie Open-Meteo-API erlaubt 5.000 Aufrufe pro Stunde und 10.000 pro Tag je IP-Adresse. Die App
cacht deshalb Gitterdaten eine Stunde und zeigt bei Überschreitung die zuletzt gespeicherten Daten.

## Bauen

Voraussetzungen: JDK 21, Android SDK mit Plattform 37 und Build-Tools 36.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # Unit-Tests, auch Screenshot-Vergleich der Diagramme
./gradlew assembleRelease        # APKs in app/build/outputs/apk/release/
```

Die Diagramme werden in den Tests aus festen Daten gerendert (Robolectric, Roborazzi) und mit den
Referenzbildern in `app/src/test/screenshots/` verglichen; zusätzlich prüft der Test im fertigen
Bild, dass der Cursor mittig auf seinem Balken steht. Nach einer gewollten Änderung des Aussehens
schreibt `./gradlew recordRoborazziDebug` neue Referenzbilder.

Signiert wird mit `keystore/keystore.properties` (nicht im Repository); fehlt die Datei, bleibt die
Release-APK unsigniert (`app-universal-release-unsigned.apk`), so wie F-Droid sie erwartet.

Technik: Kotlin, Jetpack Compose (Material 3), OkHttp, kotlinx.serialization, DataStore, WorkManager,
MapLibre Android, AboutLibraries (Lizenzliste, beim Bauen erzeugt).

### F-Droid

Store-Texte, Bildschirmfotos und Änderungshinweise liegen im F-Droid-Format unter
`fastlane/metadata/android/` (Deutsch und Englisch). Der Entwurf des Build-Rezepts für das
fdroiddata-Repository steht in `docs/fdroid/dev.nimbus.weather.yml`.

### Demo-Modus für visuelle Tests

```bash
adb shell am start -S -n dev.nimbus.weather/.MainActivity \
  --es demo_condition THUNDERSTORM --ez demo_night true \
  --es demo_season AUTUMN --es demo_wind 0.8 --es demo_pollen 0.5
```

`demo_condition`: CLEAR … THUNDERSTORM (siehe `Condition`) · `demo_season`: SPRING, SUMMER, AUTUMN,
WINTER · `demo_wind`: −1…1 · `demo_screen`: radar, places, settings · `demo_radar_temp`:
Temperatur-Verschiebung für die Radarfärbung.

### Projektstruktur

```
app/src/main/java/dev/nimbus/weather/
  data/model      Domänenmodell, WMO-Codes, Einstellungen
  data/remote     Open-Meteo, Bright Sky (DWD), Stationsnetze (GeoSphere, MeteoSwiss, DMI, METAR),
                  Pollen, Sensor.Community, Rückblick
  data/repo       Repository, Wetter jetzt (Messung und Modell), Standort, Speicher,
                  Hintergrund-Aktualisierung
  ui/background   Animierter Himmel, Jahreszeiten-Partikel
  ui/main         Wetterseite, Karten, Meteogramm, Rückblick
  ui/radar        Radar: Komposite von DWD und KNMI, RainViewer, Radarspeicher, Vorschau,
                  Farbskala, Temperatur-/Windebene
  ui/places, ui/settings, ui/components, ui/theme
  util            Einheiten und Formatierung, Sonne und Mond
```

## Lizenz

Nimbus ist freie Software unter der **GNU General Public License, Version 3 oder später**
(`LICENSES/GPL-3.0-or-later.txt`). Es gibt keine Gewährleistung, soweit gesetzlich zulässig.

Jede Datei trägt ihren Lizenzhinweis im Kopf oder in `REUSE.toml` (geprüft mit
[`reuse lint`](https://reuse.software)). Abweichend davon:

- Sonnen- und Mondberechnung in `util/Moon.kt`: teilweise nach SunCalc, © 2026 Volodymyr Agafonkin,
  BSD-2-Clause
- Gradle-Wrapper: Apache-2.0
- Aufgezeichnete API-Antworten in `app/src/test/resources/fixtures/`: Daten von Open-Meteo (auch
  MET Norway), DWD, Copernicus, GeoSphere Austria, MeteoSwiss, DMI und KNMI unter CC BY 4.0, METAR
  (US-Regierung) gemeinfrei, Sensor.Community und Nominatim unter ODbL 1.0, Pegeldaten nach den
  Bedingungen ihrer Herausgeber – im Einzelnen in `REUSE.toml`
- Screenshots in `docs/screenshots/`: CC BY 4.0, mit Wetter-, Radar- und Kartendaten von DWD,
  Open-Meteo, RainViewer, OpenFreeMap, OpenMapTiles und OpenStreetMap-Mitwirkenden (ODbL)

Idee, Entscheidungen und Tests: Thorsten Schnebeck. Geschrieben von Anthropic Claude Opus 5.5
(KI-generierter Inhalt).
