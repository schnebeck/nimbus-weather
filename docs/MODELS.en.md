<!--
  Nimbus - docs/MODELS.en.md
  Forecast models: the preset, the models to choose, regional models and a model per place.

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

<p align="right"><a href="MODELS.md">Deutsch</a></p>

# Forecast models

As of **27 October 2026** (Nimbus 2.4.3). All models come via [Open-Meteo](https://open-meteo.com);
the sun, cloud and fog the app weighs with DWD MOSMIX (below).

## Automatic (the preset)

"Automatic" is Open-Meteo's *best match*: for each place and time the finest model that fits. In
Norden (East Frisia), for example, that is KNMI Harmonie with 2 km first, ECMWF IFS with 9 km a few
days later; in Cuxhaven DWD ICON-D2 with 2 km. The data sources name the model at work with its
grid – Nimbus finds it by comparing the values of *best match* with those of the single models.

## Models to choose

| Model | Grid | Area | Range |
|---|---|---|---|
| DWD ICON (ICON-D2 / ICON-EU) | 2 km (D2), 7 km (EU), 13 km (global), finest first | Germany, Europe, worldwide | 10 days |
| ECMWF IFS | 0.25° (about 25 km) | worldwide | 10 days |
| Météo-France AROME / ARPEGE | 1.5–2.5 km (AROME), 11 km (ARPEGE), finest first | France, Western Europe, worldwide | 10 days |
| MET Nordic | 1 km | Scandinavia, northern Germany | 2½ days |
| KNMI Harmonie | 2 km | the Netherlands, north-western Germany | almost 3 days |
| DMI Harmonie | 2 km | much of northern, western and central Europe | almost 3 days |
| UK Met Office | 2 km | the British Isles and around them | about 2½ days |
| MeteoSwiss ICON-CH1 | 1 km | Switzerland, the Alps | 1½ days |
| MeteoSwiss ICON-CH2 | 2 km | Switzerland, the Alps | 5 days |
| GeoSphere AROME | 2.5 km | Austria, the Alps | almost 3 days |
| ItaliaMeteo ICON-2I | 2.2 km | Italy, the Alps | 3 days |

**Regional models** compute only for their area and only a few days. Outside their area "Automatic"
takes over completely; after their last hours it fills up the 10 days. The data sources say from
when on and with which model. On the day of the change the highest and lowest values match the hours
shown: the chosen model's up to the change, "Automatic"'s after it. What a model does not give (such
as the chance of precipitation) comes from "Automatic" as well.

## A model per place

In the list of places (long press, then tap "Model: …") each place first gets one of:

- **As in the app settings** – the place follows the setting and changes with it; the list then says,
  for example, "Model: Automatic (app setting)".
- **A model of its own for this place** – the list of models unfolds beneath it, the model in force
  marked. Only tapping a model fixes it for the place.

<p align="center"><img src="screenshots/en/models.png" width="300" alt="Forecast model of a place: the app setting or a model of its own"></p>

"My location" moves and always takes the model of the settings.

**The same place more than once:** ⧉ in the edit mode adds a place once more and opens its model
choice right away – e.g. to see MET Nordic and KNMI Harmonie for the same place side by side. Its
pages then carry the model under the name. A copy left without a model choice is discarded again.

## Sun, cloud and fog: weighed with DWD MOSMIX

Whichever model is chosen: the sunshine, cloud cover and fog of the hours to come Nimbus weighs from
the model and **DWD MOSMIX** – by what each source does best.

- **MOSMIX** is the DWD's station forecast: it reads the models (ICON, ECMWF IFS) at each station
  against many years of its measurements, in the first hours against those of now as well. So it
  corrects what a model gets wrong the same way again and again: low stratus and fog it clears too
  early, sunshine it derives from the direct beam alone. But it holds for its station, by the
  hour. Bright Sky delivers it for stations across Europe.
- **The model** computes the place itself, in quarter hours; the precipitation stays its own (and
  the radar's).

For each hour MOSMIX's sunshine and cloud cover count **80 %**, the model's the rest. A station
farther off counts less: in full up to 10 km, then less and less, from 30 km no more. One at another
height than the place (more than 300 m apart) does not count. Fog holds where the two together say
so; where the sun shines enough, it lifts it. From this follow the hour's symbol, the day, the
background and the sky "now" as long as the station does not measure it. The quarter hours (day
chart, widget) keep the model's course within the hour, their sum the hour's sunshine. The data
sources name the MOSMIX station with its distance.

For the sky "now" measurements go first: the satellite over the place, then the station
([STATIONS.en.md](STATIONS.en.md)).

## Look-back and model comparison

The look-back compares the measurements with the forecast of the model in force for the place;
outside a regional model's area with "Automatic". The "Model Comparison" card shows, independently,
the temperature of the next 72 hours from eight models: ICON-D2, ICON-EU, ECMWF IFS, ARPEGE/AROME,
UK Met Office, KNMI Harmonie, MET Norway and NOAA GFS.

## Forecast spread

The "Forecast Spread" card shows how certain the next 10 days are: ECMWF's ensemble (IFS, 0.25°,
about 25 km), 51 runs of the same model from slightly changed starting values. The regional
ensembles (ICON-D2-EPS, ICON-EU-EPS) reach only two to five days ahead, ECMWF's 15 days and
everywhere – hence one for all places, whatever model is chosen.

- Temperature, hourly: the middle run (median), the band from 10 % to 90 % of the runs and that of
  all runs.
- Precipitation per day (a day begins at the place's midnight, the hour up to midnight counts to the
  day before): the middle run's sum as a bar, the line from 10 % to 90 % of the runs, above it the
  share of runs with at least 1 mm.
- Long press and drag: a cursor on the hour, the day beneath it lighter; the legend folds away and
  a table opens – the hour's temperature, the day's high and precipitation, each for the middle
  run, 8 in 10 runs and all of them, above it the time and the chance of 1 mm. After 10 seconds
  without a touch the cursor goes and the legend comes back.
- Folded: tomorrow's high and the last day's, as 80 % of the runs have them.

The ensemble runs four times a day; the app asks for it every 6 hours at most and stores it per
place, across a restart too. When the server fails, the last one stays for up to 24 hours, marked
yellow, and is asked again after 2 minutes. For "my location" it holds while the place is at most
5 km farther on.
