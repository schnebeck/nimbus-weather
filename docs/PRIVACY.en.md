<!--
  Nimbus - docs/PRIVACY.en.md
  Privacy policy of the app (English): what leaves the phone, to whom, and what stays on it.

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

[Deutsch](PRIVACY.md)

# Privacy policy – Nimbus

Last updated: 21 October 2026

## In short

- Nimbus has **no user account, no ads, no tracking, no analytics or crash reporting** and no
  Google services in the app.
- The developer **receives no data** from you. There is no server of our own.
- To load the weather of a place, the app sends its **coordinates** to the weather services and
  data sources named below – directly from your device.
- Everything else stays **on your device**.

## Controller

Thorsten Schnebeck, e-mail: [playstore@schnebeck.de](mailto:playstore@schnebeck.de)

## Location

- With your permission the app determines your location through Android's location services
  (approximate or precise, **only while the app is open**, never in the background).
- The location is used only to load the weather for "My Location" and to give it a place name.
- The place name comes from Android's geocoder. On devices with Google services, Android sends the
  coordinates to Google for this – done by the operating system, not by Nimbus. Where there is no
  such geocoder, the app asks OpenStreetMap Nominatim.
- Without location permission the app works with places you search for.

## What is sent to whom

Every request shows the service – technically unavoidable – your device's **IP address**. The app
sends no device identifiers, no cookies and no device data; as its program name it sends only
"Nimbus-Weather (Android; …)", without version or device.

**Coordinates of a place** (rounded to four decimals, about 11 m; some services coarser) go to:

| Service | Purpose |
|---|---|
| Open-Meteo (open-meteo.com) | forecast, model comparison, air quality, pollen (CAMS), satellite sunshine in the look-back, the radar's temperature and wind layer (only to a grid of 1° to 0.125°) |
| Bright Sky (brightsky.dev) | readings of DWD stations, warnings, look-back – Germany only |
| Deutscher Wetterdienst (dwd.de) | radar values at the place, pollen region, lightning nearby |
| Sensor.Community | readings of citizen sensors around |
| PEGELONLINE (WSV) | gauges nearby |
| Saxon State Office for Environment, Agriculture and Geology | gauges in Saxony – here with the coordinates at full precision |
| European Environment Agency (EEA) | bathing waters within the chosen radius, plus the IDs of your favourite bathing sites |
| Danish Meteorological Institute (DMI), aviationweather.gov (NOAA) | station or airport nearby (an area around the place, about 100 m precise) |
| OpenStreetMap Nominatim | place name, only where Android's geocoder is missing |

**Without reference to your place** the app loads whole data sets or values by station IDs:
GeoSphere Austria, MeteoSwiss, the DWD's pollen index, NLWKN, Hochwasserportal NRW, HLNUG,
Länderübergreifendes Hochwasserportal, LAGeSo Berlin, Schleswig-Holstein (bathing waters),
Open-Meteo Marine (with the coordinates of the bathing sites).

**Radar and map:** for the map the app loads map tiles from OpenFreeMap and radar pictures from DWD,
KNMI (Netherlands), MET Norway and RainViewer, on request satellite pictures from EUMETSAT and
lightning from the DWD. The service learns which **map area** is shown; the radar pictures of the
DWD, KNMI and MET Norway are mostly loaded for their whole area.

**Place search:** what you type into the search goes to Open-Meteo (geocoding) to find matching
places.

Processing at these services is governed by their own privacy policies.

## In the background

The setting "Preload radar" (on by default) loads the radar pictures of the place where you last
opened the radar about every 30 minutes – only on Wi-Fi (or mobile data if you allow it), only with
enough battery, and only if you opened the radar in the last 7 days. These requests contain no
precise position: only whole radar pictures and the temperature and wind grid (rounded to 1°). The
app never determines a location in the background.

## On your device

Stored are settings, your saved places, the last loaded weather data (also that of "My Location"
with its coordinates), radar pictures, map tiles and other caches. An **activity log** for battery
and data analyses is off until you switch it on; it leaves the device only if you share it
yourself.

If Android's backup is on (for example to Google Drive), settings, saved places and the last loaded
weather data may be part of that backup.

You delete everything by clearing the app's data or uninstalling the app.

## Your rights

As the developer receives and stores no data of yours, there is nothing with him to disclose,
correct or delete. For data arising at the services named (such as IP addresses in their logs),
please contact them. I am glad to answer questions at
[playstore@schnebeck.de](mailto:playstore@schnebeck.de); you also have the right to complain to a data
protection supervisory authority.

## Changes

If what the app sends changes, this policy is updated; the date above shows its state. The source
code is open: <https://github.com/schnebeck/nimbus-weather>
