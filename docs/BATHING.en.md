<!--
  Nimbus - docs/BATHING.en.md
  Bathing waters: which sources Nimbus uses, per federal state, and why.

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

<p align="right"><a href="BATHING.md">Deutsch</a></p>

# Bathing waters – sources and support per German state

As of **1 October 2026** (Nimbus 1.14.0). All sources were checked live on that day.

The "Bathing waters" card shows the official EU bathing sites within an adjustable radius
(10–100 km, 50 km by default) plus favourites at any distance. As with the gauges: documented
interfaces or open licences only, no reading of web pages.

## The basis for all of Europe

| What | Source | Licence |
|---|---|---|
| position, name, type of water (lake, river, coast, transitional water), EU classification of the last 10 seasons, link to the state's bathing water profile | [EEA Bathing Water](https://water.discomap.eea.europa.eu/arcgis/rest/services/BathingWater/BathingWater_Dyna_WM/MapServer) – radius query, ~2,300 sites in Germany, ~22,000 in Europe | EEA, reuse with attribution (CC BY 4.0) |
| sea temperature at coasts and transitional waters | [Open-Meteo Marine](https://open-meteo.com/en/docs/marine-weather-api) (ocean model, updated daily) | CC BY 4.0 |

The EU classification ("excellent", "good", "sufficient", "poor") rates the hygienic quality of the
last four seasons – it says nothing about today.

## Current values of the states

During the season (mostly 15 May to 15 September) the health authorities take samples every 2–4
weeks: bacteria, transparency, water temperature, blue-green algae. Out of season Nimbus shows the
last sample with its date.

| State | Source | Content | Licence | Status |
|---|---|---|---|---|
| Berlin | LAGeSo, [letzte.csv](https://data.lageso.de/baden/0_letzte/letzte.csv) | traffic light, water temperature, transparency, E. coli/enterococci, blue-green algae notes | CC BY ([daten.berlin.de](https://daten.berlin.de/datensaetze/liste-der-badestellen-opendata-1568631)) | ✅ built in |
| Schleswig-Holstein | Ministry of Justice and Health, [open data portal](https://opendata.schleswig-holstein.de/dataset/badegewasser-messungen), `v_proben_odata.csv` | water temperature, transparency, bacteria, remark per sample | CC BY 4.0 | ✅ built in – the file holds all samples (4 MB, uncompressed): once a day and on Wi-Fi only |
| Brandenburg | KML export of badestellen.brandenburg.de | temperature, transparency, remarks, rating | terms of use say nothing about reuse | ⏸ checked, not built in |
| Mecklenburg-Western Pomerania | warnings of badewasser-mv.de (GeoJSON) | blue-green algae and cercariae warnings | undocumented endpoint of the website | ⏸ checked, not built in |
| Lower Saxony | bathing water atlas (NLGA) | samples, blue-green algae | the "interface" is only the reporting channel of the health authorities, publicly only web pages | ❌ |
| Hamburg, Saxony, Saxony-Anhalt | WFS/ArcGIS services of the geoportals | master data, partly classification | current values still to be checked | 🔍 open |
| Baden-Württemberg, Bavaria, Hesse, Rhineland-Palatinate, NRW, Saarland, Thuringia, Bremen | the states' map applications | current notes | no documented interface found | ❌ link to the profile only |

For all bathing sites without state data the EU classification, the type of water and the link to
the state's bathing water profile remain; at coasts the sea temperature as well.

Community projects reading the state portals served as signposts:
[wosatex/badegewaesser-deutschland](https://github.com/wosatex/badegewaesser-deutschland),
[technologiestiftung/badestellen](https://github.com/technologiestiftung/badestellen).

## Adding another state

State data is matched to an EEA bathing site by position (under 300 m) or by its EU id
(`DEXX_PR_…`). An open licence or an explicit permission is required; please update this page with
any change.
