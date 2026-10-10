#!/usr/bin/env python3
#
# Nimbus - tools/sunshine_calibration.py
# Checks the sunshine from the satellite against DWD stations: Open-Meteo's own sunshine and the
# estimate by the direct irradiance (SatelliteSunshine.kt), fitted on half the stations.
#
#   Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
#   Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
#   Written by Anthropic Claude Opus 5.5 - AI generated content.
#
#   Free software under the GNU General Public License, version 3 or later.
#   There is no warranty, to the extent permitted by law. The full text is in
#   LICENSES/GPL-3.0-or-later.txt.
#
# SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
# SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Usage: tools/sunshine_calibration.py [cache-dir] [first-day] [last-day]
# Station hours from Bright Sky (DWD), satellite hours from Open-Meteo (dwd_sis_europe_africa_v4,
# archived from 20 Feb 2026). Both hour-ending; the satellite is asked at the station's position.

import collections, datetime, json, math, os, subprocess, sys, time

STATIONS = ['01975', '03379', '01420', '02932', '02667', '03631', '00183', '01684', '05705',
            '02290', '02712', '01358', '04271', '01048', '03668', '01443', '05906', '05792']
CACHE = sys.argv[1] if len(sys.argv) > 1 else 'sunshine-cache'
FIRST = datetime.date.fromisoformat(sys.argv[2] if len(sys.argv) > 2 else '2026-02-20')
LAST = datetime.date.fromisoformat(sys.argv[3] if len(sys.argv) > 3 else '2026-10-09')


def get(url):
    for _ in range(4):
        try:
            return json.loads(subprocess.check_output(['curl', '-s', '--max-time', '90', url]))
        except Exception:
            time.sleep(3)
    raise SystemExit('no answer: ' + url)


def cached(name, fetch):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        json.dump(fetch(), open(path, 'w'))
    return json.load(open(path))


def station(sid):
    recs, src, d = [], None, FIRST
    while d < LAST:
        e = min(d + datetime.timedelta(days=30), LAST)
        j = get(f'https://api.brightsky.dev/weather?dwd_station_id={sid}&date={d}&last_date={e}&tz=UTC')
        own = [x for x in j['sources'] if x.get('dwd_station_id') == sid]
        src = own[0] if own else src
        ids = {x['id'] for x in own}
        recs += [{'t': w['timestamp'], 'sun': w['sunshine']} for w in j['weather']
                 if w.get('source_id') in ids and w.get('sunshine') is not None]
        d = e
    return {'src': src, 'recs': recs}


def sun_altitude(ts, lat, lon):
    """Radians; NOAA's approximation (the app uses SunCalc's – a fraction of a degree apart)."""
    d = ts / 86400.0 + 2440587.5 - 2451545.0
    g = math.radians((357.529 + 0.98560028 * d) % 360)
    q = (280.459 + 0.98564736 * d) % 360
    lam = math.radians((q + 1.915 * math.sin(g) + 0.020 * math.sin(2 * g)) % 360)
    eps = math.radians(23.439 - 0.00000036 * d)
    ra = math.atan2(math.cos(eps) * math.sin(lam), math.cos(lam))
    dec = math.asin(math.sin(eps) * math.sin(lam))
    ha = math.radians((18.697374558 + 24.06570982441908 * d) % 24 * 15 + lon - math.degrees(ra))
    phi = math.radians(lat)
    return math.asin(math.sin(phi) * math.sin(dec) + math.cos(phi) * math.cos(dec) * math.cos(ha))


def clear_sky_dni(hour_end, lat, lon):
    """Mean DNI of a clear sky over the hour (Meinel), six steps – as SatelliteSunshine.clearSkyDni."""
    total = 0.0
    for k in range(6):
        e = sun_altitude(hour_end - (5 + 10 * k) * 60, lat, lon)
        total += 0.0 if e <= 0.01 else 1367 * 0.7 ** ((1 / math.sin(e)) ** 0.678)
    return total / 6


def estimate(r, share):
    clear = r['cs'] * share
    return r['sd'] if clear < 120 else 60 * min(1.0, r['dni'] / clear)


def errors(rows, pred):
    hours, bias, n, days = 0.0, 0.0, 0, collections.defaultdict(lambda: [0.0, 0.0])
    for r in rows:
        p, s = pred(r), r['st']
        day = days[(r['sid'], r['t'] // 86400)]
        day[0] += p
        day[1] += s
        if p > 0 or s > 0:
            hours += abs(p - s)
            bias += p - s
            n += 1
    return hours / n, bias / n, sum(abs(a - b) for a, b in days.values()) / len(days)


def main():
    rows = []
    for sid in STATIONS:
        b = cached(f'bs_{sid}.json', lambda: station(sid))
        lat, lon = b['src']['lat'], b['src']['lon']
        sat = cached(f'sat_{sid}.json', lambda: get(
            'https://satellite-api.open-meteo.com/v1/archive'
            f'?latitude={lat}&longitude={lon}&hourly=sunshine_duration,direct_normal_irradiance'
            f'&models=dwd_sis_europe_africa_v4&start_date={FIRST}&end_date={LAST}&timeformat=unixtime'))['hourly']
        measured = {int(datetime.datetime.fromisoformat(x['t']).timestamp()): x['sun'] for x in b['recs']}
        for t, sd, dni in zip(sat['time'], sat['sunshine_duration'], sat['direct_normal_irradiance']):
            if sd is None or dni is None or measured.get(t) is None:
                continue
            if max(sun_altitude(t - m * 60, lat, lon) for m in (5, 30, 55)) <= 0:
                continue
            rows.append({'sid': sid, 't': t, 'sd': sd / 60, 'dni': dni, 'st': measured[t],
                         'cs': clear_sky_dni(t, lat, lon)})
    fit = set(STATIONS[0::2])
    train = [r for r in rows if r['sid'] in fit]
    test = [r for r in rows if r['sid'] not in fit]
    share = min((x / 100 for x in range(45, 96, 5)),
                key=lambda f: (lambda e: e[0] + e[2] / 60)(errors(train, lambda r: estimate(r, f))))
    print(f'{len(rows)} hours, share of the clear sky fitted on {len(fit)} stations: {share:.2f}')
    for name, pred in (('Open-Meteo', lambda r: r['sd']), ('estimate', lambda r: estimate(r, share))):
        h, b, d = errors(test, pred)
        print(f'{name:11} other stations: hour {h:5.2f} min, bias {b:+5.2f} min/h, day {d:4.0f} min')
    for season, months in (('spring', (2, 3, 4, 5)), ('summer', (6, 7, 8)), ('autumn', (9, 10, 11))):
        part = [r for r in rows if datetime.datetime.fromtimestamp(r['t'], datetime.UTC).month in months]
        if part:
            day = [errors(part, pred)[2] for pred in (lambda r: r['sd'], lambda r: estimate(r, share))]
            print(f'{season:6} all stations: day {day[0]:4.0f} -> {day[1]:4.0f} min')
    for sid in STATIONS:
        part = [r for r in rows if r['sid'] == sid]
        day = [errors(part, pred)[2] for pred in (lambda r: r['sd'], lambda r: estimate(r, share))]
        print(f'{sid}{" (fit)" if sid in fit else "      "} day {day[0]:4.0f} -> {day[1]:4.0f} min')


if __name__ == '__main__':
    main()
