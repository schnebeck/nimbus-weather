<!--
  Nimbus - README.en.md
  Short English overview of the app.

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
    <td align="center" width="33%"><img src="docs/screenshots/main.png" width="240" alt="Weather page"><br><sub><b>Weather page</b><br>readings of the nearest weather station, outlook, hourly and 10 days</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/meteogram.png" width="240" alt="Meteogram"><br><sub><b>Meteogram</b><br>temperature, rain, sunshine and wind per hour, with a slider</sub></td>
    <td align="center" width="33%"><img src="docs/screenshots/history.png" width="240" alt="Look back"><br><sub><b>Look back</b><br>measured versus forecast – just swipe right</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/radar.png" width="240" alt="Rain radar"><br><sub><b>Rain radar</b><br>DWD radar with nowcast, temperature and wind layers</sub></td>
    <td align="center"><img src="docs/screenshots/sun.png" width="240" alt="Sun and moon"><br><sub><b>Sun and moon</b><br>sun arc with golden and blue hour, moon phase</sub></td>
    <td align="center" valign="middle">
      <b>Download</b><br><br>
      <a href="https://github.com/schnebeck/nimbus-weather/releases/latest">Latest release (APK)</a><br><br>
      <sub>Android 8.0 or newer<br>no Google Play Services</sub>
    </td>
  </tr>
</table>

## Features

- Animated sky with sun, moon phase, stars, clouds, fog, rain, snow and thunderstorms; wind and gusts
  move clouds, precipitation and seasonal particles.
- Current values from the nearest weather station – DWD, GeoSphere Austria, MeteoSwiss and DMI every
  10 minutes, else an airport (METAR) –, each value only from a station at the place's height (at
  most 300 m apart) and near the model. The sky "now" follows the measured sunshine as the hours
  follow the model's: thin veil clouds do not make a cloudy sky while the sun shines through; the
  running hour in the day chart shows the same weather as the header. The ⓘ of "Measured at …" names
  for each value whether it was measured (and where) or comes from the model.
- DWD weather alerts, precipitation nowcast, hourly and 10-day forecast with chance of
  precipitation, detail tiles, air quality, pollen, citizen sensors and a comparison of eight
  weather models.
- Forecast model: preset "Automatic" – the finest model for each place and time, named with its
  grid in the data sources – or one of DWD ICON, ECMWF IFS, Météo-France, MET Nordic (1 km), KNMI
  Harmonie, DMI Harmonie, UK Met Office, MeteoSwiss ICON-CH1/-CH2, GeoSphere AROME and ItaliaMeteo
  ICON-2I. Each place first follows the app setting or gets a model of its own; outside a regional
  model's area and after its last hours "Automatic" takes over. ⧉ adds a place once more, e.g. to see
  two models side by side.
- Day meteogram (temperature every 15 minutes forecast, every 10 minutes measured; today: station
  readings for the hours already over, then the forecast – also in the precipitation and pressure
  charts), look back at the past days (measured versus forecast, hour by hour as a table,
  precipitation measured and forecast with its chance) with the rain radar of the whole day in
  5-minute steps, sun and moon card with light phases.
- Bathing waters: all official EU bathing sites within an adjustable radius plus favourites, with
  EU classification, sea temperature at coasts and the latest samples (water temperature, blue-green
  algae) where states publish them openly – see [docs/BATHING.md](docs/BATHING.md).
- Rain radar: DWD for Germany, KNMI for the Netherlands, RainViewer for the rest of Europe (the time
  line follows the place's radar), one colour scale for rain (green → yellow →
  red → magenta) and snow (turquoise → white → violet), optionally calmer in blue and pink–violet;
  radar cells smoothed at every zoom level instead of blocks; smooth playback (the motion of the
  rain is computed between two radar images); an own radar store keeps every image, loaded once and
  processed, until it expires – panning and zooming need no network; roads, borders and names above
  the radar; temperature (with isotherms) and wind layers, satellite and warning map. The
  precipitation map on the weather page shows the same picture for its area around the place,
  loading only that area's radar cells, in a lane of its own beside the radar loop.
- An ⓘ on every card explains the terms. Units follow the country on first start; the order and visibility of
  the cards can be set in the settings, places are sorted or deleted after a long press.
- My location: while it is shown (its page, the list of places) the position is looked for on opening
  and after 5 minutes – by cell/Wi-Fi first, by GPS as well where that gives nothing (e.g. on an EDGE
  network); a dot on the location pin tells whether the position is current (green) or older (yellow,
  then the cards too); searches without result wait longer each time (2, 5, 10 minutes). For saved
  places the GPS stays off. Pulling to reload (or tapping the pin) asks for the position first –
  everything for "My location" is yellow and waits until it is confirmed or the new place is taken;
  then the cards turn green one by one. A reload fetches everything anew (no cache, station lists and
  tides too); if a request fails, the old data stay, yellow, and are tried again next time.
- Shelf life: each part of the data has its own – forecast, station, citizen sensors and flood
  alerts 10 minutes, gauges 15, air quality and bathing waters 60, pollen 3 hours. Past it, the
  part tells so itself – its dot turns yellow that very moment (no timer redrawing the page) – and
  only that part is loaded again; each card turns green as soon as its source has answered. With
  the position out of date, so are all data of "My location". Stored data nobody needs is deleted: data of a moment after 4 days (as far as the
  look-back reaches), lists (gauges, bathing waters, tides) after 30 days without use, the weather
  of removed places at once.
- Tablets: two columns of cards from 600 dp, a places sidebar in landscape on large tablets; phones
  held sideways show three equal columns: the header on the left, the cards in the other two, clear of the camera cut-out.

## Data sources

DWD (via [Bright Sky](https://brightsky.dev) and the DWD GeoServer), [Open-Meteo](https://open-meteo.com)
(best match, DWD ICON, MET Norway's MET Nordic and the other models, Copernicus CAMS),
[GeoSphere Austria](https://data.hub.geosphere.at), [MeteoSwiss](https://opendatadocs.meteoswiss.ch),
[DMI](https://www.dmi.dk/friedata), airport reports (METAR, [aviationweather.gov](https://aviationweather.gov)),
[KNMI](https://english.knmidata.nl/open-data) radar, [RainViewer](https://www.rainviewer.com),
[Sensor.Community](https://sensor.community), [OpenFreeMap](https://openfreemap.org) ·
© OpenMapTiles · © OpenStreetMap contributors.

## Building

JDK 21 and the Android SDK (platform 37) are required:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest assembleRelease
```

The unit tests also render the charts from fixed data (Robolectric, Roborazzi) and compare them with
the reference images in `app/src/test/screenshots/`; `./gradlew recordRoborazziDebug` writes new ones
after an intended change of the look.

## License

Nimbus is free software under the **GNU General Public License, version 3 or later**. Every file
states its license in its header or in `REUSE.toml` ([REUSE](https://reuse.software) compliant); the
sun and moon calculation is partly based on SunCalc (BSD-2-Clause); recorded test data are licensed
CC BY 4.0 (Open-Meteo, MET Norway, DWD, Copernicus, GeoSphere Austria, MeteoSwiss, DMI, KNMI), public
domain (METAR) or ODbL 1.0 (Sensor.Community, Nominatim), gauge data by their publishers' terms –
see `REUSE.toml`.

Idea, decisions and testing: Thorsten Schnebeck. Written by Anthropic Claude Opus 5.5 (AI generated
content).
