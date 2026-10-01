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
rain, snow or a starry sky. The data comes from the German Weather Service (DWD) – readings from
the nearest weather station, forecasts from the high-resolution ICON-D2 model and the rain radar.
No ads, no account, no Google services. The app speaks English and German.

<p align="center">
  <img src="docs/screenshots/scenes.png" width="820" alt="Animated weather scenes: sun, autumn leaves, thunderstorm, snow, rain, clear night">
  <br><sub>The sky follows the weather, the position of the sun and the season (demo mode shown).</sub>
</p>

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/screenshots/main.png" width="240" alt="Weather page"><br><sub><b>Weather page</b><br>readings of the nearest DWD station, outlook, hourly and 10 days</sub></td>
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
- Current values from the nearest DWD station, DWD weather alerts, precipitation nowcast, hourly and
  10-day forecast with chance of precipitation, detail tiles, air quality, pollen, citizen sensors and
  a comparison of eight weather models.
- Day meteogram, look back at the past days (measured versus forecast) with the rain radar of the
  whole day in 5-minute steps, sun and moon card with light phases.
- Bathing waters: all official EU bathing sites within an adjustable radius plus favourites, with
  EU classification, sea temperature at coasts and the latest samples (water temperature, blue-green
  algae) where states publish them openly – see [docs/BATHING.md](docs/BATHING.md).
- Rain radar: DWD for Germany, RainViewer for Europe, one colour scale for rain and snow, temperature
  and wind layers, satellite and warning map.
- An ⓘ on every card explains the terms. Units follow the country on first start; the order and visibility of
  the cards can be set in the settings, places are sorted or deleted after a long press.
- Tablets: two columns of cards from 600 dp, a places sidebar in landscape on large tablets.

## Data sources

DWD (via [Bright Sky](https://brightsky.dev) and the DWD GeoServer), [Open-Meteo](https://open-meteo.com)
(DWD ICON and other models, Copernicus CAMS), [RainViewer](https://www.rainviewer.com),
[Sensor.Community](https://sensor.community), [OpenFreeMap](https://openfreemap.org) ·
© OpenMapTiles · © OpenStreetMap contributors.

## Building

JDK 21 and the Android SDK (platform 37) are required:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew testDebugUnitTest assembleRelease
```

## License

Nimbus is free software under the **GNU General Public License, version 3 or later**. Every file
states its license in its header or in `REUSE.toml` ([REUSE](https://reuse.software) compliant); the
sun and moon calculation is partly based on SunCalc (BSD-2-Clause), recorded test data are licensed
CC BY 4.0 / ODbL 1.0.

Idea, decisions and testing: Thorsten Schnebeck. Written by Anthropic Claude Opus 5.5 (AI generated
content).
