<!--
  Nimbus - docs/STATIONS.md
  Measured values now: which station networks Nimbus uses, which station stands for a place, and why.

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

<p align="right"><a href="STATIONS.en.md">English</a></p>

# Messwerte – Stationsnetze

Stand: **5. Oktober 2026** (Nimbus 1.34.1).

Mit der Einstellung „Aktuelle Werte: Messung statt Modell“ (voreingestellt an) zeigt die Wetterseite
oben die Werte der nächsten Wetterstation statt der Modellwerte für den Ort. Der Hinweis
„Gemessen an DWD-Station Norderney (13,5 km)“ nennt die Station der Temperatur; ⓘ daran listet für
jeden Wert, ob er gemessen ist (und an welcher Station) oder vom Modell kommt.

## Netze

| Gebiet | Netz | Takt | Messwerte | Lizenz |
|---|---|---|---|---|
| Deutschland | DWD über [Bright Sky](https://brightsky.dev) | 10 Minuten | Temperatur, Taupunkt, Feuchte, Luftdruck, Wind, Böen, Sichtweite, Bewölkung, Niederschlag, Wetter (Regen, Schnee, Nebel, Gewitter), Sonnenschein | CC BY 4.0 (DWD) |
| Österreich | [GeoSphere Austria](https://data.hub.geosphere.at), TAWES | 10 Minuten | Temperatur, Taupunkt, Feuchte, Luftdruck, Wind, Böen, Niederschlag | CC BY 4.0 |
| Schweiz | [MeteoSwiss](https://opendatadocs.meteoswiss.ch), SwissMetNet | 10 Minuten | Temperatur, Taupunkt, Feuchte, Luftdruck, Wind, Böen | CC BY 4.0 |
| Dänemark | [DMI](https://www.dmi.dk/friedata), metObs | 10 Minuten | Temperatur, Taupunkt, Feuchte, Luftdruck, Wind, Böen, Sichtweite, Bewölkung, Niederschlag | CC BY 4.0 |
| weltweit | Flughäfen (METAR) über [aviationweather.gov](https://aviationweather.gov) | 30 Minuten, Temperatur in ganzen Grad | Temperatur, Taupunkt, Luftdruck, Wind, Böen, Sichtweite (bis 10 km), Bewölkung, Wetter | gemeinfrei (US-Regierung) |

Alle Netze kommen ohne Konto und ohne Schlüssel aus. Die Stationslisten von GeoSphere und
MeteoSwiss hält die App eine Woche, „Neu laden“ holt sie frisch; beim DMI fragt sie nur die
Stationen um den Ort ab.

## Welche Station für den Ort steht

1. **Nah:** höchstens 30 km entfernt, die Messung höchstens zwei Stunden alt.
2. **Netz vor Flughafen:** Ein nationales 10-Minuten-Netz geht vor; ein Flughafen nur, wo keines
   misst. Darunter die nächste Station.
3. **In der Höhe des Ortes:** Jeder Wert zählt nur von einer Station, die höchstens 300 m höher
   oder tiefer liegt als der Ort – 300 m machen etwa 2 K und entscheiden zwischen Regen und Schnee,
   Nebel und Sonne. Die Talstation misst nicht den Gipfel, die Gipfelstation nicht das Tal. Nur der
   auf Meereshöhe umgerechnete Luftdruck gilt in jeder Höhe.
4. **Nahe am Modell:** Weicht die gemessene Temperatur mehr als 10 K vom Modell ab, gehört die
   Messung zu einem anderen Ort; die Station gibt dann nichts.
5. **Jeder Wert mit seiner Station:** Bright Sky füllt Werte, die die Hauptstation gerade nicht
   meldet, von anderen Stationen auf. Für jeden solchen Wert gelten 3. und 4. mit dessen eigener
   Station; fehlt er, nimmt die App zuerst den letzten Stundenwert der Hauptstation selbst.

## Der Himmel „jetzt“

Die Bewölkung der Modelle zählt auch dünne, hohe Schleierwolken mit, durch die die Sonne scheint.
Deshalb gilt für den Himmel dieselbe Regel wie für die Stunden der Vorhersage:

- Beobachtet die Station Regen, Schnee, Nebel oder Gewitter, gilt das.
- Sonst hellt der **gemessene Sonnenschein** der letzten halben Stunde (sonst der letzten Stunde)
  den Himmel auf: ab 45 Minuten je Stunde sonnig, ab 15 Minuten teils bewölkt. Wenig gemessene Sonne
  lässt den Himmel, wie er ist.
- Misst die Station keinen Sonnenschein, zählt der Sonnenschein des Modells in der laufenden Stunde.
- Nachts gibt es keinen Sonnenschein zu messen; der Himmel bleibt der des Modells.

Die laufende Stunde im Tagesdiagramm zeigt dasselbe Wetter wie die Kopfzeile.

## Geprüft, aber nicht eingebaut

| Netz | Befund |
|---|---|
| KNMI (Niederlande) | Stationsdaten nur über die Open-Data-API mit persönlichem Schlüssel |
| MET Norway (Frost) | nur mit registrierter Client-ID |
| Météo-France | nur mit persönlichem API-Schlüssel |

Für diese Netze müsste jede Nutzerin und jeder Nutzer einen eigenen Schlüssel beantragen, den die
App verschlüsselt auf dem Gerät speichert. Das ist noch offen. Die Niederlande, Norwegen und
Frankreich bekommen bis dahin die Werte der Flughäfen.
