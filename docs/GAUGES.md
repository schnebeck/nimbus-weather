<!--
  Nimbus - docs/GAUGES.md
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

# Pegel und Gezeiten – Ländersupport

Stand: **30. September 2026** (Nimbus 1.12.0). Alle Quellen wurden an diesem Tag live geprüft.

Die Pegelkarte zeigt die Pegel im Umkreis von 10 km, **je Gewässer einen**, höchstens vier
(Hann. Münden: Weser, Fulda, Werra). Gibt es dort keinen, erscheint der nächstgelegene bis 20 km
(z. B. im Osten Hannovers der Leinepegel Herrenhausen). Tidepegel zählen bis 25 km und stehen oben,
mit selbst berechneter Gezeitenvorhersage. Kanäle werden übersprungen.

Nimbus nutzt nur Quellen mit **dokumentierter Schnittstelle oder offener Lizenz**. Webseiten
auszulesen (Scraping) ist ausgeschlossen: Es bricht bei jeder Umgestaltung und ist bei mehreren
Ländern durch deren Nutzungsbedingungen nicht gedeckt.

## Übersicht

| Land | Quelle | Messwerte | Verlauf | Warnstufen | Lizenz / Bedingungen |
|---|---|---|---|---|---|
| Alle (Bundeswasserstraßen) | [PEGELONLINE](https://www.pegelonline.wsv.de/webservice/dokuRestapi) (WSV) | ✅ | 7 Tage, Tide 36 h + Vorhersage | Hochwassermarken I–III, MNW/MW/MHW | DL-DE Zero 2.0 |
| Niedersachsen | [NLWKN Pegelonline](https://www.pegelonline.nlwkn.niedersachsen.de/pdf/BenutzerhandbuchWebservicePegelonline.pdf) | ✅ | 7 Tage | Meldestufe 1–3 | frei, Quellenangabe Pflicht |
| Nordrhein-Westfalen | [Hochwasserportal NRW](https://www.hochwasserportal.nrw) (LANUK), `…/data/internet/stations/` | ✅ | 7 Tage | Informationswert 1–3, MNW/MW/MHW | DL-DE Zero 2.0 ([Open.NRW](https://ckan.open.nrw.de/de/dataset/44fd75ca-f306-424d-aeec-4f6854306277)) |
| Sachsen | [LfULG Pegelnetz](https://luis.sachsen.de/arcgis/rest/services/wasser/pegelnetz/FeatureServer), Layer `aktuelle_Wasserstaende` | ✅ | – (nur aktueller Wert) | Alarmstufe 1–4, Tendenz | im Open-Data-Katalog, Quellenangabe |
| Hessen | [HLNUG WISKI-Web](https://www.hlnug.de/themen/wasser/wasserstands-und-durchflusswerte-pegel/wiski-web) | ✅ | 7 Tage + **Vorhersage** | Meldestufe 1–3 | „unter Angabe HLNUG als Quelle nutzbar“ |
| alle 16 Länder | [LHP-PublicAPI](https://www.hochwasserzentralen.de/developers/) | ❌ nur Einstufung | – | Hochwasser-Klasse 0–4 + Link zur Landesseite; **regionale Hochwasserwarnungen** | CC BY 4.0 |

Pegel ohne eigene Messwert-Quelle (alle übrigen Länder) erscheinen mit der LHP-Einstufung
(„Kein Hochwasser“, „Meldestufe 1“ …) und einem Link zur Pegelseite des Landes. Die regionalen
Hochwasserwarnungen des LHP erscheinen in der Warnungskarte, wenn der Ort im Warngebiet liegt
(Flussabschnitte: im Umkreis von 5 km).

## Geprüft, aber nicht eingebaut

| Land | Befund | Grund |
|---|---|---|
| Bayern | GKD bietet Downloads nur als Auftragssystem (Warteschlange, Nutzungsbedingungen bestätigen); Download-Daten CC BY 4.0. Kartendienst des LfU liefert nur Stammdaten und Link. | keine Live-Schnittstelle; Webseiten nur zum privaten Gebrauch |
| Rheinland-Pfalz | JSON-API `hochwasser.rlp.de/api/v1` | Nutzungsbedingungen: keine kommerzielle Vervielfältigung – verträgt sich nicht mit der GPL |
| Sachsen-Anhalt | WISKI-JSON wie Hessen | keine offene Lizenz („Eigentum des LHW“) |
| Schleswig-Holstein | Open Data je Pegel (JSON/CSV) | nur Tagesmittel, live nur per Scraping (hsi-sh.de) |
| Berlin | Wasserportal-API (CSV/WaterML) | „tagesaktuell“, getesteter Pegel ohne aktuelle Werte; Spree/Havel deckt PEGELONLINE ab |
| Baden-Württemberg, Thüringen, Mecklenburg-Vorpommern, Brandenburg, Hamburg, Bremen, Saarland | Portale ohne Schnittstelle | nur per Scraping |
| alle (inoffiziell) | [hochwasserzentralen.api.bund.dev](https://hochwasserzentralen.api.bund.dev/) | liefert seit 2023 leere Antworten |

Community-Projekte, die die Länderportale auslesen (Scraping), dienten als Wegweiser:
[stephan192/lhpapi](https://github.com/stephan192/lhpapi), [klaffka/ha-laenderpegel](https://github.com/klaffka/ha-laenderpegel).

## Ein weiteres Land aufnehmen

Jede Quelle liefert Kandidaten (`candidates`) und bei Bedarf Details (`details`); die Auswahl je
Gewässer, das Zusammenführen doppelter Pegel und die LHP-Einstufung übernimmt
`GaugeSource.select`. Länder mit KISTERS WISKI web (wie NRW und Hessen) brauchen nur eine
neue `WiskiSource.Config`. Voraussetzung ist eine offene Lizenz oder eine ausdrückliche
Nutzungserlaubnis; bei Änderungen bitte diese Seite aktualisieren.
