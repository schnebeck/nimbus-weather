# Nimbus – Wetter für Deutschland & Europa

Private, nicht-kommerzielle Android-Wetter-App (Benchmark-Projekt) nach dem Vorbild von Apples Wetter-App:
animierter Himmel passend zum aktuellen Wetter, Regenradar mit DWD-Nowcast und offene Datenquellen.
Oberfläche auf Englisch (Standard) und Deutsch.

## Installation

Die fertigen, signierten APKs liegen in `dist/`:

| Datei | Für |
|---|---|
| `Nimbus-1.4.0-arm64.apk` (13 MB) | praktisch alle Android-Handys seit ca. 2017 |
| `Nimbus-1.4.0-universal.apk` (44 MB) | alle Geräte inkl. 32-Bit-ARM und x86 |

APK aufs Handy kopieren, öffnen, „Installation aus unbekannten Quellen“ für den Dateimanager erlauben.
Mindestversion: Android 8.0 (API 26). Google Play Services werden nicht benötigt.

## Funktionen

- **Animierter Hintergrund**: Himmelsverlauf nach Sonnenstand (inkl. Dämmerung), Sonne, Mond in der echten
  Mondphase, Sterne, Sternschnuppen, ziehende Wolken, Nebel, Niesel/Regen/Starkregen, Schnee, Gewitter mit Blitzen.
  Wind (Richtung, Stärke, **Böen**) bewegt Wolken, Regen, Schnee und Partikel.
- **Jahreszeiten-Partikel** bei trockenem Wetter: Blüten (Frühling), Pusteblumen-Samen und Glühwürmchen
  (Sommer), fallende Blätter mit Herbstfärbung (Herbst), Eiskristalle bei Frost (Winter). Südhalbkugel gespiegelt.
  **Pollen** schweben entsprechend der echten CAMS-Pollenbelastung.
- **Bedienung:** Orte (aktueller Standort + gespeicherte Orte) und Einstellungen über das Menü oben links,
  oben rechts „Radar“. **Wischen nach rechts** führt in den **Rückblick**: „Heute bisher“, „Gestern“,
  „Vorgestern“ – gemessene DWD-Stationswerte (Temperatur, Niederschlag, Sonne, Wind, Stundenwerte) neben der
  Vorhersage des gewählten Modells für dieselben Stunden, inkl. mittlerer Abweichung (außerhalb Deutschlands
  nur Modellwerte). Die Daten werden beim Wischen nachgeladen, nicht dauerhaft gespeichert.
- **Hintergrund-Aktualisierung** stündlich (WorkManager, nur mit Netz und ausreichend Akku) für alle Orte;
  der aktuelle Standort wird dabei an seiner zuletzt bekannten Position aktualisiert.
- **Wetterseite** wie bei Apple:
  großer, beim Scrollen einklappender Kopfbereich mit **Kombisymbol** (Sonne/Mond, Wolken, Regen/Schnee,
  Windstriche bei Wind), DWD-Warnungen, Niederschlag der nächsten 3 Stunden (15-Min.-Schritte),
  Stundenvorhersage mit Sonnenauf-/-untergang, 10-Tage-Vorhersage mit Temperaturbalken
  (antippen → Tagesverlauf), **Niederschlagskarte 24 h** (Wahrscheinlichkeit und Menge pro Stunde),
  Niederschlagswahrscheinlichkeit in Stunden- und Tagesvorhersage immer angezeigt (unter 10 % blass),
  Radar-Vorschau, Kacheln für Gefühlt, UV, Wind (Kompass), Luftfeuchte/Taupunkt, Sichtweite (mit Angabe
  „gemessen an DWD-Station“ bzw. „Modellwert“), Luftdruck (Tendenz),
  Sonnenlauf, **Mond** (Phase, Beleuchtung, Auf-/Untergang, nächster Vollmond), Luftqualität, Pollen,
  **Pollenflug** (Stufen je Art für bis zu 3 Tage, Zusammensetzung der nächsten 24 h, gestapelter Stundenverlauf),
  **Community-Messnetz** und **Modellvergleich** (8 Wettermodelle, 72 h).
- **Wetter-Lexikon**: ⓘ an jeder Karte erklärt Begriffe wie Niederschlagswahrscheinlichkeit, Taupunkt,
  gefühlte Temperatur, Luftdruck-Tendenz, Warnstufen, Radar oder Mondphasen (EN/DE).
- **Kurzvorhersage „Nächste Stunden“** ganz oben: automatisch aus den Stundendaten erzeugter Text zu
  Wetterwechsel, Temperaturverlauf, Niederschlagsrisiko, Böen und (ab 15 Uhr) Ausblick auf morgen.
- **Regenradar** (MapLibre, OpenFreeMap-Karte in Schiefer-Blau passend zu den Glaskarten): DWD-Radarkomposit mit 2-h-Nowcast für Deutschland,
  RainViewer für Europa, beide auf **eine gemeinsame Farbskala** umgerechnet (dBZ-basiert):
  **Regen (über 0 °C) weiß → grau → hellblau → dunkelblau**, **Schnee (ab 0 °C) blassrosa → dunkelviolett**,
  Schneeregen (0–1 °C) als Mischfarbe – entschieden pro Pixel über die 2-m-Temperatur zur Zeit des Radarbilds.
  Overlays **Temperatur** (Flächen gleicher Temperatur in 2-°C-Stufen mit Isolinien + Werte) und **Wind**
  (Pfeile nach Stärke gefärbt, km/h), passend zur Stunde auf der Zeitleiste; das Werteraster wird beim Zoomen
  feiner (1° → 0,5° → 0,25° → 0,125°, je 99 Punkte); Satellit (Meteosat), DWD-Warnkarte.
  Die Radarschleife lädt vom aktuellen Bild ausgehend in Gruppen und startet, sobald 5 Bilder da sind.
  Rückblick 2 h / 6 h / 24 h (DWD hält 3 Tage vor), Kartenbeschriftung in App-Sprache.
  Vergangene Radarbilder werden bis zu 3 Tage gecacht; im WLAN wird die Radarschleife für den
  angezeigten Ort **im Hintergrund vorgeladen**.
- **Orte**: GPS-Standort + gespeicherte Orte (Suche über Open-Meteo Geocoding), Wischen zum Wechseln.
- **Einstellungen**: Vorhersagemodell (DWD ICON, Beste Auswahl, ECMWF, Météo-France), Einheiten
  (°C/°F, km/h, m/s, mph, kn, Bft, mm/in), DWD-Stationswerte, Animationen an/aus.
- Offline: letzte Daten je Ort werden gespeichert und mit Hinweis angezeigt.

## Datenquellen

| Zweck | Quelle | Hinweis |
|---|---|---|
| Vorhersage (Standard) | DWD ICON-D2 (2,2 km) → ICON-EU → ICON global via [Open-Meteo](https://open-meteo.com) | ICON-D2 für die ersten ~48 h in Mitteleuropa |
| Lückenfüller (UV, Tage 9–10) | Open-Meteo „best match“ | |
| Aktuelle Messwerte | DWD-Stationen (SYNOP) via [Bright Sky](https://brightsky.dev) | nächste Station ≤ 30 km, ersetzt Modellwerte |
| Warnungen | DWD via Bright Sky | nur Deutschland |
| Radar Deutschland | DWD GeoServer WMS `Radar_wn-product_1x1km_ger` | 5-Min.-Takt, 2 h Nowcast, 3 Tage Archiv |
| Radar Europa | [RainViewer](https://www.rainviewer.com/api.html) | nur die letzten 2 h, max. Zoom 7 |
| Satellit, Warnkarte | DWD GeoServer WMS | |
| Luftqualität | Copernicus CAMS Europe via Open-Meteo | |
| Pollenflug Deutschland | [DWD-Pollenflug-Gefahrenindex](https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json) | 8 Arten, 27 Regionen, heute/morgen/übermorgen; Region per WMS-Abfrage „Pollenfluggebiete“ |
| Pollen Europa, Zusammensetzung, Verlauf | Copernicus CAMS via Open-Meteo | 6 Arten, stündlich, ~3 Tage |
| Community-Messnetz | [Sensor.Community](https://sensor.community) | Median aller Außensensoren, Ausreißer gefiltert |
| Modellvergleich | Open-Meteo (ICON-D2, ICON-EU, ECMWF, AROME/ARPEGE, UKMO, KNMI, MET Norway, GFS) | |
| Temperatur-/Windgitter (Radar-Overlays, Regen/Schnee) | Open-Meteo, 11 × 9 Punkte, 1° bis 0,125° je nach Zoom | 1 h gecacht (Speicher + Datei); feine Gitter nur bei eingeschaltetem Overlay |
| Rückblick (Messungen) | DWD-Stationen via Bright Sky `/weather` | MOSMIX-Vorhersagewerte werden herausgefiltert |
| Rückblick (Vorhersage) | Open-Meteo `past_days=2`, gewähltes Modell | |
| Mond | lokal berechnet (Meeus, SunCalc) | gegen US Naval Observatory getestet |
| Karte | [OpenFreeMap](https://openfreemap.org) · © OpenMapTiles · © OpenStreetMap-Mitwirkende | |

**Kachelmannwetter/Meteologix** wurde geprüft: Es gibt nur die offizielle API (Plus-Abo nötig), keine freie
Community-Schnittstelle; Scraping der Webseite verstößt gegen die Nutzungsbedingungen. Daher nicht eingebaut.

## Bauen

Voraussetzungen: JDK 21, Android SDK mit Plattform 37 und Build-Tools 36.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # 73 Unit-Tests
./gradlew assembleRelease        # APKs in app/build/outputs/apk/release/
```

Signiert wird mit `keystore/keystore.properties` (nicht im Repository). Fehlt die Datei, wird die Release-APK
mit dem Debug-Schlüssel signiert.

Stack: Kotlin 2.4, Jetpack Compose (Material 3), AGP 9.4, Gradle 9.8, OkHttp 5, kotlinx.serialization,
DataStore, MapLibre Android 13 (OpenGL-Variante).

## Visuelle Tests (Demo-Modus)

Szenen lassen sich per Intent erzwingen, z. B. für Screenshots:

```bash
adb shell am start -S -n dev.nimbus.weather/.MainActivity \
  --es demo_condition THUNDERSTORM --ez demo_night true \
  --es demo_season AUTUMN --es demo_wind 0.8 --es demo_pollen 0.5
```

`demo_condition`: CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN, FREEZING_RAIN,
SLEET, SNOW, HEAVY_SNOW, SHOWERS, THUNDERSTORM · `demo_season`: SPRING, SUMMER, AUTUMN, WINTER ·
`demo_wind`: −1…1 (Vorzeichen = Richtung) · `demo_screen`: radar, places, settings ·
`demo_radar_temp`: Temperatur-Verschiebung für die Radarfärbung (z. B. `-15` → Niederschlag als Schnee).

## Hinweis zu Open-Meteo

Die freie Open-Meteo-API erlaubt 5.000 Aufrufe pro Stunde und 10.000 pro Tag **je IP-Adresse**; jeder
Ort einer Mehrfachabfrage zählt einzeln. Das Temperaturgitter (99 Punkte) wird deshalb eine Stunde
gecacht. Bei Überschreitung (HTTP 429) zeigt die App die zuletzt gespeicherten Daten, das Radar färbt
dann ohne Temperaturinformation (Regen-Palette bzw. RainViewer-Schneemarkierung).

## Projektstruktur

```
app/src/main/java/dev/nimbus/weather/
  data/model      Domänenmodell, WMO-Codes, Einstellungen
  data/remote     Open-Meteo, Bright Sky (DWD), Sensor.Community
  data/repo       Repository (Quellen zusammenführen), Standort, Speicher/Cache
  ui/background   Animierter Himmel, Jahreszeiten-Partikel
  ui/main         Wetterseite, Karten, Kacheln
  ui/radar        Radar (MapLibre), Farbskala/Umfärbung, Zeitleiste
  ui/places, ui/settings, ui/components (Glas-Karten, Icons, Lexikon, Mond)
  util            Einheiten/Formatierung, Mondberechnung
```
