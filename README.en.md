<!--
  Nimbus - README.en.md
  Overview in English: features, data sources, building and licensing.

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

<p align="right"><a href="README.md">Deutsch</a></p>

# Nimbus – weather for Germany and Europe

Nimbus reflects the current weather with small matching background animations – drifting clouds,
rain, snow or a starry sky. The data comes from Europe's weather services – readings from the
nearest weather station (DWD, GeoSphere Austria, MeteoSwiss, DMI, else an airport), the forecast of
the finest model for the place or of a model of your choice, per place too, and the rain radar of
the DWD and the KNMI. No ads, no account, no Google services. The app speaks English and German.

<p align="center">
  <img src="docs/screenshots/scenes.png" width="820" alt="Animated weather scenes: sun, autumn leaves, thunderstorm, snow, rain, clear night">
  <br><sub>The sky follows the weather, the position of the sun and the season (demo mode shown).</sub>
</p>

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/en/main.png" width="240" alt="Weather page"><br><sub><b>Weather page</b><br>readings of the nearest weather station, outlook, hourly and 10 days</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/en/meteogram.png" width="240" alt="Meteogram"><br><sub><b>Meteogram</b><br>temperature, rain, sunshine and wind per hour – expected values as frames</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/en/history.png" width="240" alt="Look back"><br><sub><b>Look back</b><br>measured versus expected – rain by the radar, sunshine by the satellite over the place</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/en/radar.png" width="240" alt="Rain radar"><br><sub><b>Rain radar</b><br>DWD radar with nowcast, temperature and wind layers</sub></td>
    <td align="center"><img src="docs/screenshots/en/sun.png" width="240" alt="Sun and moon"><br><sub><b>Sun and moon</b><br>sun arc with golden and blue hour, moon phase</sub></td>
    <td align="center" valign="middle">
      <b>Download</b><br><br>
      <a href="https://github.com/schnebeck/nimbus-weather/releases/latest">Latest release (APK)</a><br><br>
      <sub>Android 8.0 or newer<br>no Google Play Services</sub>
    </td>
  </tr>
</table>

## Installation

The signed APKs are under [Releases](https://github.com/schnebeck/nimbus-weather/releases/latest):

| File | For |
|---|---|
| `Nimbus-<version>-arm64.apk` | practically all Android phones since about 2017 |
| `Nimbus-<version>-universal.apk` | all devices including 32-bit ARM and x86 |

Copy the APK to the phone, open it and allow installing from unknown sources. Requires Android 8.0
(API 26); Google Play Services are not needed.

What the app sends to whom and what stays on the phone: [privacy policy](https://schnebeck.github.io/nimbus-weather/PRIVACY.en.html)
(project page: <https://schnebeck.github.io/nimbus-weather/index.en.html>).

## Features

- **Animated sky**: colours by the sun's position, sun, moon in its real phase, stars, clouds, fog,
  rain, snow and thunderstorms. Wind and gusts move clouds, precipitation and particles; in dry
  weather blossoms, seeds, leaves or ice crystals drift by the season, pollen by the real load.
- **Weather page**: combined symbol, temperature with unit and max/min, a short forecast for the
  next hours, DWD alerts, lightning nearby (the nearest flash of the last 15 minutes within 50 km,
  tapped the radar with the lightning), "Precipitation today" on top (amount, next 3 hours, highest chance; on dry
  days one line with the next precipitation, or hidden), hourly and 10-day forecast with the chance
  of precipitation (each day with a symbol and its weather from its hours: sky, rain, snow, thunder
  and fog in one symbol, and a line such as "drizzle early in the morning, then bright" or "mostly
  cloudy with showers"), tiles for feels-like, UV, wind, humidity, visibility and pressure. The forecast
  appears at once, tiles with slower sources follow as soon as their data is there; a dot per tile
  shows whether its data is current (green) or still the previous one (yellow). Each kind of data has
  a shelf life (forecast 10, look-back 15 minutes): on returning and every minute after, the app
  loads what has expired.
  Current values come, where possible, from the nearest weather station: DWD, GeoSphere Austria,
  MeteoSwiss and DMI every 10 minutes, else an airport (METAR). Each value counts only from a station
  at the place's height (at most 300 m apart) and near the model. The sky "now" follows the measured
  sunshine as the hours follow the model's – thin veil clouds do not make a cloudy sky while the sun
  shines through; the running hour in the day chart shows the same weather as the header. The day's high and low
  hold the temperature now (the model may stay below it; measured, it has been reached). The ⓘ of
  "Measured at …" names for each value whether it was measured (and where) or comes from the model.
  Networks and rules: [docs/STATIONS.en.md](docs/STATIONS.en.md).
- **Meteogram** per day (00–24 h): temperature (forecast every 15 minutes, the DWD station's readings
  every 10 minutes), precipitation, sunshine and wind per hour, night shading, a legend with the
  day's totals. The station's 10-minute readings as 30-minute means, the forecast continuing from the
  last reading without a step. Today the dashed curve ("Forecast so far") shows what had been
  forecast for the hours over; with the setting "Today: measured vs. forecast" the coloured curve
  shows the readings only and the dashed one the whole day's forecast, as the look-back's course of
  the day does. Precipitation as a chart of its own below the
  temperature, with the chance as a line (the default), or in the temperature chart (in the
  look-back too). A long press shows a cursor with all values of
  the hour, in the columns "measured | expected"; on days to come the whole day's overview stands there without the cursor. What was measured stands as a filled bar, what is
  expected as a frame – precipitation blue, sunshine orange. Today the meteogram, precipitation and
  pressure show the readings for the hours already over, the forecast after them; an hour whose
  reading is still on its way shows the forecast's frame until then, an hour without a reading stays empty, the legend says "not measured". Precipitation is measured by the DWD radar
  over the place, sunshine by the satellite – not by a station 20–40 km away
  ([docs/STATIONS.en.md](docs/STATIONS.en.md)); below the chart it says where the measurements come from.
  A shower of a few minutes the radar map shows over the place counts as precipitation everywhere –
  in the notice ("Light rain from about 16:15"), in the symbol, in the widget and in the look-back,
  where it reads ">0 mm" when no measurement caught it; weak echoes only: "Possibly some drizzle".
- **Look back** by swiping right: today so far, yesterday, the day before – DWD readings against the
  forecast of the chosen model, on top the day in parts (early to night) with symbol and weather, the
  sky showing them one after the other; the mean deviation, the hourly values as a table
  measured | expected; precipitation measured and forecast with its chance; in the day's summary
  temperature, precipitation and sunshine each beside the forecast; lightning within 15 km
  (by the satellite) makes an hour a thunderstorm – in its symbol, the table and the day's summary
  with count and time span; and the rain radar of the
  whole day in 5-minute steps to play, pan and zoom (where a weather service radar measures: DWD,
  KNMI, MET Norway).
- **Sun and moon**: sun arc on a fixed scale per place (the height of the arc shows the season),
  light phases day, golden hour, blue hour and night, day length; moon phase, rise and set.
- **Environment**: air quality, pollen (DWD index and composition, species to choose – e.g. trees
  only), citizen sensors, a comparison of eight weather models.
- **Tides and water levels**: the gauges within 10 km, one per water body (e.g. Weser, Fulda and
  Werra in Hann. Münden), free places with further water bodies up to 15 km. At the coast and on
  tidal rivers first the next high and low waters with a tide curve – computed from four weeks of
  gauge readings, about ±30 minutes, not for navigation or mudflat hiking. Readings from the federal
  waterways and from Lower Saxony, NRW, Saxony and Hesse, elsewhere the flood classification of the
  state portal with a link; plus the states' flood warnings. Which source per state and why:
  [docs/GAUGES.en.md](docs/GAUGES.en.md).
- **Bathing waters**: all official EU bathing sites within an adjustable radius (10–100 km) – lakes,
  rivers, coasts –, favourites at any distance; EU classification, sea temperature at the coast, in
  Berlin and Schleswig-Holstein the latest samples with water temperature and blue-green algae
  notes. Sources per state: [docs/BATHING.en.md](docs/BATHING.en.md).
- **Rain radar**: DWD radar with a 2-hour nowcast for Germany, KNMI radar for the Netherlands, MET
  Norway's radar for Norway, Sweden, Finland and Denmark (1 km, zoomed out an overview in coarser
  cells), RainViewer for the rest of Europe (every 10 minutes, the steps between computed from the
  motion; the time line follows the place's radar), one colour scale for
  rain or snow per pixel by the temperature: by default calm in blue (rain sky blue → blue →
  violet → magenta, snow grey → white; readable with red-green colour blindness too), optionally
  contrast (rain green → yellow → red → magenta, snow turquoise → white → violet); the legend
  starts at the weakest step (drizzle, about 0.1 mm/h) and names the steps with mm/h; the radar
  cells smoothed at every zoom
  level instead of blocks; zoom down to about 5 km across, the radar keeps its zoom for the next
  time and the preview card; the radar opens on the current picture, loads the forecast first, then
  the past, and starts playing once the forecast is there (can be switched off); each time round it
  stands two seconds on "now"; in the forecast what lies beyond its area is grey (no forecast, not
  dry); smooth playback:
  between two radar images the motion of the rain is
  computed; an own radar store – each image of a weather service (all of Germany, the Netherlands in
  one) is loaded once, turned into reflectivity and kept locally until it expires (analyses ~3½ days),
  panning and zooming need no network; opaque colours, roads, borders and names above the radar;
  temperature (with isotherms) and wind layers, satellite (Meteosat, every 10 minutes, at the time of
  the radar image; while playing blended between the pictures, in the look-back every 30 minutes;
  off in the radar's forecast – there is no picture of it), lightning (from the satellite, all of Europe: each flash a point
  where it struck, shrinking from its minute on and fading within 15 minutes – smoothly while playing), warning map, look-back up to 24 h. The radar of a day in
  the look-back shows temperature, wind, satellite and lightning too. The loop waits only for the
  source of the picture's middle; neighbouring countries and RainViewer come in a lane of their own
  and complete the picture, a source that does not answer rests a few minutes. Which layers are on when it
  opens is set in the settings. It plays at 20 pictures per second; after 2 minutes untouched the
  radar closes and goes back where it was opened. For someone who opened the radar within the last
  week, the app loads the past images of the 2-hour loop of the place last seen on the radar ahead –
  about every 15 minutes while it is open, otherwise about every 30 minutes in the background while
  the battery is not low; on Wi-Fi or, if so set, over mobile data too (the radar forecast is fetched
  by the radar itself – it is new every 5 minutes). The radar resolution follows the device's memory. Without a connection the radar shows the
  stored images; nothing waits forever. The precipitation map on the weather page shows the same
  picture for its area around the place, loading only that area's radar cells, in a lane of its own
  beside the radar loop.
- **Battery**: the animated sky runs at 30 frames/s (rain, snow: 60), after a minute without touch at
  15; in the system's battery saver, with the system's animations off or the animation switched off it
  stands still – without rain, snow, lightning and leaves. Nimbus works only while it is shown –
  apart from loading the radar ahead (can be switched off) and the widget (only while one is on the
  home screen): no timers behind the lock screen, and the radar stops too when the phone is locked. On opening the app loads what has expired – until then it shows the last data with a
  yellow dot –, the look-back for the place shown only.
- **Accessibility**: large system font up to 200 % and small displays from 320 dp – nothing overlaps
  or is cut off, long words are hyphenated or shortened; the 10-day rows go to two lines with a large
  font.
- **Full screen**: a double tap on the sky above the cards hides and shows the status and navigation
  bars, optionally by a button too; with three-button navigation the app ends above the buttons.
- **Glossary**: the ⓘ on every card explains the terms.
- **My location**: while it is shown (its page, the list of places) the position is looked for on
  opening and after 5 minutes – by cell/Wi-Fi first, by GPS as well where that gives nothing (e.g. on
  an EDGE network). A dot on the location pin tells whether the position is current (green) or older
  (yellow, then the cards too); searches without result wait longer each time (2, 5, 10 minutes). For
  saved places the GPS stays off. Reloading (or tapping the pin) asks for the position first –
  everything for "My location" is yellow and waits until it is confirmed or the new place is taken;
  then the cards turn green one by one. A reload fetches everything anew (no cache, station lists and
  tides too); if a request fails, the old data stay, yellow, and are tried again next time.
- **Shelf life**: each part of the data has its own – forecast, station, citizen sensors and flood
  alerts 10 minutes, gauges 15, air quality and bathing waters 60, pollen 3 hours. Past it, the part
  tells so itself – its dot turns yellow that very moment (no timer redrawing the page) – and only
  that part is loaded again; each card turns green as soon as its source has answered. With the
  position out of date, so are all data of "My location". Stored data nobody needs is deleted: data
  of a moment after 4 days (as far as the look-back reaches), lists (gauges, bathing waters, tides)
  after 30 days without use, the weather of removed places at once.
- **Places, settings**: my location and saved places (long press to sort and delete); units (on
  first start to suit the country), cards, radar, display (station values, full screen,
  animations), pollen, bathing waters, the forecast model (one line, the list on a tap; preset:
  Automatic – the finest model for each place and time, named with its grid in the data sources).
- **Tidying the weather page**: hold a card – fold/expand, hide (with undo), arrange (every card
  shrinks to its title line, drag the chosen one to its new place; "Position changed · Undo",
  "Cancel" leaves all as it was). The small tiles alike, among themselves in their block. Notes
  with "Undo" can be wiped aside. Three views by importance: compact (precipitation, hours,
  10 days, radar, pollen), normal (all, sun, moon, air quality and the special cards folded),
  detailed (all open) – under Settings → Cards and once as a question at the page's end until
  answered (also "All fine as it is"). The order stays. The 10-day forecast folds to "Today", larger cards (sun, moon, air quality, pressure, pollen, gauges, bathing
  waters, sensor network, model comparison) fold to one line with the arrow in their title; the app
  keeps that per card, and whether "Today" in the 10-day forecast is open or closed. Under
  "Diagnostics" an activity log (off until switched on): network calls
  with their amounts, shown or not, screen and CPU time, and how the radar loads (its sources, when
  its own one is complete, how the others answer, when all pictures are prepared, sources resting,
  loading ahead in the background), for battery and data analyses – without
  coordinates or place names, on the phone until shared.
- **Notices** (off by default, each kind on its own): official weather warnings of the DWD from
  the level chosen (the warnings card shows the same level, weaker ones in a line there), gauges at
  the place shown and those marked with the star when they reach an alert level, the states' flood
  warnings if wished, and "Rain ahead": only after three dry hours and when the radar surely shows
  rain within the next hour, at most twice a day. Each notice once, again only at a higher level;
  it goes when its reason is over. The source is written out below it. Do not disturb and bedtime
  apply as set; with do not disturb no rain notice. The place shown last is checked about every
  15–30 minutes, without locating in the background. No replacement for NINA or Cell Broadcast.
- **Home-screen widget** ("Clock & weather", on the lock screen too where the system offers it):
  on top the date and the place, below them the time and the weather (symbol, temperature, high
  and low, with a precipitation symbol its chance), under the clock the next alarm; below over the
  whole width the next two hours on the clock's quarter hours – a chart of sunshine (share of the
  quarter hour in %, behind) and precipitation (mm/h, in front), only when there is something to
  draw, else a line ("No precipitation, no sunshine until 12:30") – with the precipitation card's
  line and the chance of its hour ("… from about 11:00 · 40%"), and the chosen pollen types flying
  today, the strongest first: as gauges with their half steps beside the chart (a third of the
  width), below it where that does not fit, with little room only those flying noteworthily as
  coloured points. The date with the month written out (the weekday where
  there is room) or in figures with the calendar week. Each part can be switched off; what does
  not fit its size it leaves out (from Android 12 on for each size the launcher shows it in), the height left goes to the chart or spreads between the groups;
  "As of" with ↻ at the bottom. A tap on the time opens the alarms, on the date the calendar, on
  the weather Nimbus at the widget's place. The system's colours (light/dark, from Android 12 on
  from the wallpaper), adjustable opacity. Place: "My Location" – where the app last found the
  phone; the widget does not look for it itself (no location in the background): after moving
  elsewhere open Nimbus once or tap the weather – or a saved place. It fetches the weather about
  every 30 minutes and on ↻; what the app has just loaded it takes over at once.
- **A model per place**: in the list of places (long press) each place first gets "As in the app
  settings" or "A model of its own for this place", then the model – regional ones too, like MET
  Nordic (1 km) or KNMI Harmonie (2 km); outside their area and after their last hours "Automatic"
  takes over. ⧉ adds a place once more, e.g. to see two models side by side. All models with grid,
  area and range: [docs/MODELS.en.md](docs/MODELS.en.md).
- **Tablet, landscape**: from 600 dp width the cards in two columns (a card that would stand alone in
  its row across the full width), phones held sideways in three equal columns: the header on the
  left, the cards in the other two (nothing under the camera cut-out); on large tablets in landscape
  a places sidebar on the left; settings, places, look-back and radar controls centred at a readable
  width. "Open-source licenses" lists the licences of Nimbus and of all libraries used. Offline the
  data loaded last.

## Data sources

| Purpose | Source |
|---|---|
| Forecast | [Open-Meteo](https://open-meteo.com): preset "best match", to choose DWD ICON, ECMWF IFS, Météo-France, MET Nordic (MET Norway), KNMI Harmonie, DMI Harmonie, UK Met Office, MeteoSwiss ICON-CH1/-CH2, GeoSphere AROME, ItaliaMeteo ICON-2I; gaps from "best match" |
| Current readings | DWD stations via [Bright Sky](https://brightsky.dev), [GeoSphere Austria](https://data.hub.geosphere.at), [MeteoSwiss](https://opendatadocs.meteoswiss.ch), [DMI](https://www.dmi.dk/friedata), airports (METAR, [aviationweather.gov](https://aviationweather.gov)) |
| Alerts, look-back | DWD alerts and stations via Bright Sky |
| Measured over the place | Precipitation: DWD radar RADOLAN (RW, RY), Germany · sunshine: DWD from EUMETSAT MTG satellite data via [Open-Meteo](https://open-meteo.com/en/docs/satellite-radiation-api), Europe |
| Radar, warning map | DWD GeoServer, [KNMI](https://english.knmidata.nl/open-data) (Netherlands), [MET Norway](https://www.met.no/en/free-meteorological-data) (Nordic composite); rest of Europe: [RainViewer](https://www.rainviewer.com/api.html) |
| Satellite | Meteosat (MTG, GeoColour) via [EUMETView](https://view.eumetsat.int) – "Contains modified EUMETSAT Meteosat data", CC BY 4.0 |
| Lightning | DWD GeoServer: flashes of Meteosat Third Generation's Lightning Imager (EUMETSAT), Europe and Africa |
| Air quality, pollen in Europe | Copernicus CAMS via Open-Meteo |
| Pollen in Germany | [DWD pollen hazard index](https://opendata.dwd.de/climate_environment/health/alerts/s31fg.json) |
| Citizen sensors | [Sensor.Community](https://sensor.community) |
| Model comparison, temperature/wind grid | Open-Meteo |
| Water levels, tides, floods | PEGELONLINE (WSV), NLWKN, LANUK NRW, LfULG Sachsen, HLNUG, Länderübergreifendes Hochwasserportal – details in [docs/GAUGES.en.md](docs/GAUGES.en.md) |
| Bathing waters | European Environment Agency, LAGeSo Berlin, Schleswig-Holstein, sea temperature Open-Meteo – details in [docs/BATHING.en.md](docs/BATHING.en.md) |
| Name of my location | the system geocoder, else [Nominatim](https://nominatim.org) (OpenStreetMap) |
| Sun and moon | computed on the device (after SunCalc and J. Meeus) |
| Map | [OpenFreeMap](https://openfreemap.org) · © OpenMapTiles · © OpenStreetMap contributors |

The free Open-Meteo API allows 5,000 calls per hour and 10,000 per day per IP address. So the app
caches grid data for an hour and shows the data stored last when the limit is reached.

## Building

Requires JDK 21 and the Android SDK with platform 37 and build tools 36.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest      # unit tests, including the screenshot comparison of the charts
./gradlew assembleRelease        # APKs in app/build/outputs/apk/release/
```

The tests render the charts from fixed data (Robolectric, Roborazzi) and compare them with the
reference images in `app/src/test/screenshots/`; a test also checks in the finished image that the
cursor stands centred on its bar. After an intended change of the look,
`./gradlew recordRoborazziDebug` writes new reference images.

Signing uses `keystore/keystore.properties` (not in the repository); without it the release APK
stays unsigned (`app-universal-release-unsigned.apk`), as F-Droid expects it.

Technology: Kotlin, Jetpack Compose (Material 3), OkHttp, kotlinx.serialization, DataStore,
WorkManager, MapLibre Android, AboutLibraries (licence list, generated at build time).

### F-Droid

Store texts, screenshots and change notes are in the F-Droid format under
`fastlane/metadata/android/` (German and English). The draft build recipe for the fdroiddata
repository is in `docs/fdroid/dev.nimbus.weather.yml`.

### Demo mode for visual tests

```bash
adb shell am start -S -n dev.nimbus.weather/.MainActivity \
  --es demo_condition THUNDERSTORM --ez demo_night true \
  --es demo_season AUTUMN --es demo_wind 0.8 --es demo_pollen 0.5
```

`demo_condition`: CLEAR … THUNDERSTORM (see `Condition`) · `demo_season`: SPRING, SUMMER, AUTUMN,
WINTER · `demo_wind`: −1…1 · `demo_screen`: radar, places, settings · `demo_radar_temp`:
temperature offset for the radar colouring.

### Project structure

```
app/src/main/java/dev/nimbus/weather/
  data/model      domain model, WMO codes, settings
  data/remote     Open-Meteo, Bright Sky (DWD), station networks (GeoSphere, MeteoSwiss, DMI, METAR),
                  pollen, Sensor.Community, look-back, radar and satellite over the place
  data/repo       repository, weather now (measurement and model), location, storage,
                  shelf life of the data
  ui/background   animated sky, seasonal particles
  ui/main         weather page, cards, meteogram, look-back
  ui/radar        radar: composites of the DWD, the KNMI and MET Norway, RainViewer, radar store, preview,
                  colour scale, temperature/wind layers
  ui/places, ui/settings, ui/components, ui/theme
  widget          Home-screen widget: content, views, setup, fetching
  util            units and formatting, sun and moon
```

## Licence

Nimbus is free software under the **GNU General Public License, version 3 or later**
(`LICENSES/GPL-3.0-or-later.txt`). There is no warranty, to the extent permitted by law.

Every file states its licence in its header or in `REUSE.toml` (checked with
[`reuse lint`](https://reuse.software)). Deviating from it:

- Sun and moon calculation in `util/Moon.kt`: partly after SunCalc, © 2026 Volodymyr Agafonkin,
  BSD-2-Clause
- Gradle wrapper: Apache-2.0
- Recorded API responses in `app/src/test/resources/fixtures/`: data of Open-Meteo (MET Norway too),
  DWD, Copernicus, GeoSphere Austria, MeteoSwiss, DMI and KNMI under CC BY 4.0, METAR (US government)
  in the public domain, Sensor.Community and Nominatim under ODbL 1.0, gauge data by their
  publishers' terms – in detail in `REUSE.toml`
- Screenshots in `docs/screenshots/`: CC BY 4.0, with weather, radar, satellite and map data of DWD
  (sunshine from EUMETSAT data), Open-Meteo, RainViewer, OpenFreeMap, OpenMapTiles and OpenStreetMap
  contributors (ODbL)

Idea, decisions and testing: Thorsten Schnebeck. Written by Anthropic Claude Opus 5.5 (AI generated
content).
