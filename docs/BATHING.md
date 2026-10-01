<!--
  Nimbus - docs/BATHING.md
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

# Badegewässer – Quellen und Ländersupport

Stand: **1. Oktober 2026** (Nimbus 1.14.0). Alle Quellen wurden an diesem Tag live geprüft.

Die Kachel „Badegewässer“ zeigt die amtlichen EU-Badestellen im einstellbaren Umkreis (10–100 km,
Standard 50 km) und dazu Favoriten in beliebiger Entfernung. Wie bei den Pegeln gilt: nur
dokumentierte Schnittstellen oder offene Lizenzen, kein Auslesen von Webseiten.

## Grundlage für ganz Europa

| Was | Quelle | Lizenz |
|---|---|---|
| Lage, Name, Gewässerart (See, Fluss, Küste, Übergangsgewässer), EU-Einstufung der letzten 10 Saisons, Link zum Badegewässerprofil des Landes | [EEA Bathing Water](https://water.discomap.eea.europa.eu/arcgis/rest/services/BathingWater/BathingWater_Dyna_WM/MapServer) – Umkreisabfrage, ~2.300 Stellen in Deutschland, ~22.000 in Europa | EEA, Wiederverwendung mit Quellenangabe (CC BY 4.0) |
| Meerestemperatur an Küsten und Übergangsgewässern | [Open-Meteo Marine](https://open-meteo.com/en/docs/marine-weather-api) (Ozeanmodell, täglich aktuell) | CC BY 4.0 |

Die EU-Einstufung („ausgezeichnet“, „gut“, „ausreichend“, „mangelhaft“) bewertet die
hygienische Qualität der letzten vier Saisons – sie sagt nichts über den heutigen Tag.

## Aktuelle Werte der Länder

Die Gesundheitsämter beproben in der Saison (meist 15. Mai bis 15. September) alle 2–4 Wochen:
Bakterien, Sichttiefe, Wassertemperatur, Blaualgen. Außerhalb der Saison zeigt Nimbus die
letzte Probe mit Datum.

| Land | Quelle | Inhalt | Lizenz | Status |
|---|---|---|---|---|
| Berlin | LAGeSo, [letzte.csv](https://data.lageso.de/baden/0_letzte/letzte.csv) | Ampel, Wassertemperatur, Sichttiefe, E. coli/Enterokokken, Blaualgen-Hinweise | CC BY ([daten.berlin.de](https://daten.berlin.de/datensaetze/liste-der-badestellen-opendata-1568631)) | ✅ eingebaut |
| Schleswig-Holstein | Ministerium für Justiz und Gesundheit, [Open-Data-Portal](https://opendata.schleswig-holstein.de/dataset/badegewasser-messungen), `v_proben_odata.csv` | Wassertemperatur, Sichttiefe, Bakterien, Bemerkung je Probe | CC BY 4.0 | ✅ eingebaut – die Datei enthält alle Proben (4 MB, unkomprimiert): einmal täglich und nur im WLAN |
| Brandenburg | KML-Export von badestellen.brandenburg.de | Temperatur, Sichttiefe, Bemerkungen, Bewertung | Nutzungsbedingungen ohne Angabe zur Weiterverwendung | ⏸ geprüft, nicht eingebaut |
| Mecklenburg-Vorpommern | Warnhinweise von badewasser-mv.de (GeoJSON) | Blaualgen-, Zerkarien-Warnungen | nicht dokumentierter Endpunkt der Webseite | ⏸ geprüft, nicht eingebaut |
| Niedersachsen | Badegewässer-Atlas (NLGA) | Proben, Blaualgen | die „Schnittstelle“ ist nur der Meldeweg der Gesundheitsämter, öffentlich nur Webseiten | ❌ |
| Hamburg, Sachsen, Sachsen-Anhalt | WFS/ArcGIS-Dienste der Geoportale | Stammdaten, teils Einstufung | aktuelle Werte noch zu prüfen | 🔍 offen |
| Baden-Württemberg, Bayern, Hessen, Rheinland-Pfalz, NRW, Saarland, Thüringen, Bremen | Kartenanwendungen der Länder | aktuelle Hinweise | keine dokumentierte Schnittstelle gefunden | ❌ nur Link zum Profil |

Für alle Badestellen ohne Länderdaten bleiben EU-Einstufung, Gewässerart und der Link zum
Badegewässerprofil des Landes; an Küsten zusätzlich die Meerestemperatur.

Community-Projekte, die Länderportale auslesen, dienten als Wegweiser:
[wosatex/badegewaesser-deutschland](https://github.com/wosatex/badegewaesser-deutschland),
[technologiestiftung/badestellen](https://github.com/technologiestiftung/badestellen).

## Ein weiteres Land aufnehmen

Länderdaten werden über die Lage (unter 300 m) oder die EU-Kennung (`DEXX_PR_…`) einer
EEA-Badestelle zugeordnet. Voraussetzung ist eine offene Lizenz oder ausdrückliche
Nutzungserlaubnis; bei Änderungen bitte diese Seite aktualisieren.
