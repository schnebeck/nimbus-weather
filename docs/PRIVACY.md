<!--
  Nimbus - docs/PRIVACY.md
  Privacy policy of the app: what leaves the phone, to whom, and what stays on it.

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

[English](PRIVACY.en.md)

# Datenschutzerklärung – Nimbus

Stand: 23. Oktober 2026

## Kurz gesagt

- Nimbus hat **kein Nutzerkonto, keine Werbung, kein Tracking, keine Analyse- oder Absturzdienste**
  und keine Google-Dienste in der App.
- Der Entwickler **erhält keine Daten** von dir. Es gibt keinen eigenen Server.
- Um Wetterdaten für einen Ort zu laden, schickt die App dessen **Koordinaten** an die
  Wetterdienste und Datenquellen, die unten genannt sind – direkt von deinem Gerät.
- Alles andere bleibt **auf deinem Gerät**.

## Verantwortlich

Thorsten Schnebeck, E-Mail: [playstore@schnebeck.de](mailto:playstore@schnebeck.de)

## Standort

- Mit deiner Erlaubnis bestimmt die App deinen Standort über die Standortdienste von Android
  (ungefährer oder genauer Standort, **nur während die App geöffnet ist**, nie im Hintergrund).
- Der Standort dient nur dazu, das Wetter für „Mein Standort“ zu laden und ihm einen Ortsnamen zu
  geben.
- Den Ortsnamen ermittelt der Geocoder von Android. Auf Geräten mit Google-Diensten schickt Android
  dafür die Koordinaten an Google – das geschieht durch das Betriebssystem, nicht durch Nimbus. Gibt
  es keinen solchen Geocoder, fragt die App OpenStreetMap Nominatim.
- Ohne Standorterlaubnis funktioniert die App mit selbst gesuchten Orten.

## Was an wen gesendet wird

Jede Anfrage zeigt dem jeweiligen Dienst – technisch unvermeidbar – die **IP-Adresse** deines
Geräts. Die App sendet keine Geräte-Kennungen, keine Cookies und keine Gerätedaten; als
Programmkennung sendet sie nur „Nimbus-Weather (Android; …)“ ohne Version und Gerät.

**Koordinaten eines Ortes** (auf vier Nachkommastellen gerundet, etwa 11 m; einzelne Dienste
gröber) gehen an:

| Dienst | Zweck |
|---|---|
| Open-Meteo (open-meteo.com) | Vorhersage, Modellvergleich, Luftqualität, Pollen (CAMS), Satelliten-Sonnenschein im Rückblick, Temperatur- und Windebene des Radars (nur auf ein Raster von 1° bis 0,125° genau) |
| Bright Sky (brightsky.dev) | Messwerte der DWD-Stationen, Warnungen, Rückblick – nur in Deutschland |
| Deutscher Wetterdienst (dwd.de) | Radarwerte am Ort, Pollenregion, Blitze in der Nähe |
| Sensor.Community | Messwerte von Bürger-Sensoren im Umkreis |
| PEGELONLINE (WSV) | Pegel in der Nähe |
| Landesamt für Umwelt, Landwirtschaft und Geologie Sachsen | Pegel in Sachsen – hier mit den Koordinaten in voller Genauigkeit |
| Europäische Umweltagentur (EEA) | Badegewässer im gewählten Umkreis, dazu die Kennungen deiner Lieblings-Badestellen |
| Dänemarks Wetterdienst (DMI), aviationweather.gov (NOAA) | Station bzw. Flughafen in der Nähe (Gebiet um den Ort, etwa 100 m genau) |
| OpenStreetMap Nominatim | Ortsname, nur wenn der Geocoder von Android fehlt |

**Ohne Bezug zu deinem Ort** lädt die App ganze Datensätze oder Werte über Stations-Kennungen:
GeoSphere Austria, MeteoSwiss, Pollenflug-Gefahrenindex des DWD, NLWKN, Hochwasserportal NRW, HLNUG,
Länderübergreifendes Hochwasserportal, LAGeSo Berlin, Schleswig-Holstein (Badegewässer),
Open-Meteo Marine (mit den Koordinaten der Badestellen).

**Radar und Karte:** Für die Karte lädt die App Kartenkacheln von OpenFreeMap und Radarbilder von
DWD, KNMI (Niederlande), MET Norway und RainViewer, auf Wunsch Satellitenbilder von EUMETSAT und
Blitze vom DWD. Dabei erfährt der jeweilige Dienst, welcher **Kartenausschnitt** angezeigt wird;
die Radarbilder des DWD, des KNMI und von MET Norway werden meist für das ganze Gebiet geladen.

**Ortssuche:** Was du in die Suche tippst, geht an Open-Meteo (Geocoding), um passende Orte zu
finden.

Für die Verarbeitung bei diesen Diensten gelten deren eigene Datenschutzbestimmungen.

## Im Hintergrund

Die Einstellung „Radar vorladen“ (voreingestellt an) lädt etwa alle 30 Minuten die Radarbilder des
Ortes vor, an dem du das Radar zuletzt geöffnet hast – nur im WLAN (oder mobil, wenn du es
erlaubst), nur bei ausreichendem Akku und nur, wenn du das Radar in den letzten 7 Tagen geöffnet
hast. Diese Anfragen enthalten keine genaue Position: nur ganze Radarbilder und das Temperatur- und
Windraster (auf 1° gerundet).

Liegt ein **Nimbus-Widget** auf dem Startbildschirm, holt es etwa alle 30 Minuten (und wenn du es
antippst) das Wetter für seinen Ort – dieselben Abrufe wie die App beim Öffnen, also mit den
Koordinaten dieses Ortes an dieselben Dienste. Zeigt es „Mein Standort“, nimmt es die Position, die
die App zuletzt bestimmt hat, während sie geöffnet war. Ohne Widget gibt es diese Abrufe nicht.

Schaltest du die **Hinweise** ein (voreingestellt aus), prüft Nimbus etwa alle 15 bis 30 Minuten den
zuletzt angezeigten Ort auf Wetterwarnungen, Pegelstufen und kommenden Regen – dieselben Abrufe wie
die App beim Öffnen, mit den Koordinaten dieses Ortes; für mit Stern markierte Pegel die Pegeldienste
in deren Umgebung. Die Benachrichtigungen entstehen auf deinem Gerät; was gemeldet wurde, merkt sich
die App nur dort.

Einen Standort bestimmt die App im Hintergrund nie.

## Auf deinem Gerät

Gespeichert werden Einstellungen, deine gespeicherten Orte, die zuletzt geladenen Wetterdaten (auch
die von „Mein Standort“ mit seinen Koordinaten), Radarbilder, Kartenkacheln und andere
Zwischenspeicher. Ein **Aktivitätsprotokoll** für Akku- und Datenanalysen ist aus, bis du es
einschaltest; es verlässt das Gerät nur, wenn du es selbst teilst.

Wenn die Datensicherung von Android eingeschaltet ist (zum Beispiel in Google Drive), können
Einstellungen, gespeicherte Orte und die zuletzt geladenen Wetterdaten Teil dieser Sicherung sein.

Alles löschst du, indem du die App-Daten löschst oder die App deinstallierst.

## Deine Rechte

Da der Entwickler keine Daten von dir erhält oder speichert, gibt es bei ihm nichts auszukunften,
zu berichtigen oder zu löschen. Zu Daten, die bei den genannten Diensten anfallen (etwa
IP-Adressen in deren Protokollen), wende dich bitte an diese. Fragen beantworte ich gern unter
[playstore@schnebeck.de](mailto:playstore@schnebeck.de); du hast außerdem das Recht, dich bei einer
Datenschutz-Aufsichtsbehörde zu beschweren.

## Änderungen

Ändert sich, welche Daten die App sendet, wird diese Erklärung angepasst; das Datum oben zeigt den
Stand. Der Quellcode ist offen: <https://github.com/schnebeck/nimbus-weather>
