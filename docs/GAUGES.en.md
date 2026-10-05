<!--
  Nimbus - docs/GAUGES.en.md
  Water levels and tides: which sources Nimbus uses per federal state, and why.

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

<p align="right"><a href="GAUGES.md">Deutsch</a></p>

# Water levels and tides – support per German state

As of **30 September 2026** (Nimbus 1.12.0). All sources were checked live on that day.

The gauge card shows the gauges within 10 km, **one per water body**, at most four (Hann. Münden:
Weser, Fulda, Werra). Where places are left, further water bodies up to 15 km are added
(Braunschweig: Schunter at Harxbüttel, 8.3 km, and Oker at Groß Schwülper, 11.6 km). With no gauge
nearby at all, the nearest one up to 20 km appears (e.g. in the east of Hanover the Leine gauge
Herrenhausen). Tide gauges count up to 25 km and come first, with a tide forecast computed by the
app. Canals are skipped.

Nimbus uses only sources with a **documented interface or an open licence**. Reading web pages
(scraping) is ruled out: it breaks with every redesign and, for several states, is not covered by
their terms of use.

## Overview

| State | Source | Readings | History | Warning levels | Licence / terms |
|---|---|---|---|---|---|
| All (federal waterways) | [PEGELONLINE](https://www.pegelonline.wsv.de/webservice/dokuRestapi) (WSV) | ✅ | 7 days, tide 36 h + forecast | flood marks I–III, MNW/MW/MHW | DL-DE Zero 2.0 |
| Lower Saxony | [NLWKN Pegelonline](https://www.pegelonline.nlwkn.niedersachsen.de/pdf/BenutzerhandbuchWebservicePegelonline.pdf) | ✅ | 7 days | alert level 1–3 | free, attribution required |
| North Rhine-Westphalia | [Hochwasserportal NRW](https://www.hochwasserportal.nrw) (LANUK), `…/data/internet/stations/` | ✅ | 7 days | information value 1–3, MNW/MW/MHW | DL-DE Zero 2.0 ([Open.NRW](https://ckan.open.nrw.de/de/dataset/44fd75ca-f306-424d-aeec-4f6854306277)) |
| Saxony | [LfULG Pegelnetz](https://luis.sachsen.de/arcgis/rest/services/wasser/pegelnetz/FeatureServer), layer `aktuelle_Wasserstaende` | ✅ | – (current value only) | alarm level 1–4, trend | in the open data catalogue, attribution |
| Hesse | [HLNUG WISKI-Web](https://www.hlnug.de/themen/wasser/wasserstands-und-durchflusswerte-pegel/wiski-web) | ✅ | 7 days + **forecast** | alert level 1–3 | "usable with HLNUG named as the source" |
| all 16 states | [LHP-PublicAPI](https://www.hochwasserzentralen.de/developers/) | ❌ classification only | – | flood class 0–4 + link to the state's page; **regional flood warnings** | CC BY 4.0 |

Gauges without a source of their own readings (all other states) appear with the LHP
classification ("no flood", "alert level 1" …) and a link to the state's gauge page. The LHP's
regional flood warnings appear in the warnings card when the place lies in the warning area (river
sections: within 5 km).

## Checked, not built in

| State | Finding | Reason |
|---|---|---|
| Bavaria | GKD offers downloads only as an order system (queue, terms to confirm); download data CC BY 4.0. The LfU map service gives master data and a link only. | no live interface; web pages for private use only |
| Rhineland-Palatinate | JSON API `hochwasser.rlp.de/api/v1` | terms of use: no commercial reproduction – does not fit the GPL |
| Saxony-Anhalt | WISKI JSON as in Hesse | no open licence ("property of the LHW") |
| Schleswig-Holstein | open data per gauge (JSON/CSV) | daily means only, live only by scraping (hsi-sh.de) |
| Berlin | Wasserportal API (CSV/WaterML) | "updated daily", the gauge tested without current values; PEGELONLINE covers Spree/Havel |
| Baden-Württemberg, Thuringia, Mecklenburg-Western Pomerania, Brandenburg, Hamburg, Bremen, Saarland | portals without an interface | scraping only |
| all (unofficial) | [hochwasserzentralen.api.bund.dev](https://hochwasserzentralen.api.bund.dev/) | empty answers since 2023 |

Community projects reading the state portals (scraping) served as signposts:
[stephan192/lhpapi](https://github.com/stephan192/lhpapi), [klaffka/ha-laenderpegel](https://github.com/klaffka/ha-laenderpegel).

## Adding another state

Each source gives candidates (`candidates`) and, where needed, details (`details`); the choice per
water body, merging duplicate gauges and the LHP classification are done by `GaugeSource.select`.
States with KISTERS WISKI web (like NRW and Hesse) only need a new `WiskiSource.Config`. An open
licence or an explicit permission is required; please update this page with any change.
