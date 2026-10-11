#!/usr/bin/env python3
#
# Nimbus - tools/sunshine_calibration.py
# Learns the sunshine of an hour – measured by the satellite (with the model's low cloud) and
# forecast by the model – checked against DWD stations, and writes the models the app evaluates
# (SatelliteSunshine.kt, ForecastSunshine.kt).
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
# Usage: tools/sunshine_calibration.py [cache-dir] [first-day] [last-day]   (needs numpy, scikit-learn)
# Station hours: Bright Sky (DWD). Satellite hours: Open-Meteo, dwd_sis_europe_africa_v4 (archived
# from 20 Feb 2026), at the station's position. Low cloud and the forecast: Open-Meteo's archived
# forecasts (best_match, as the app asks by default). All hour-ending, UTC.
# The check: every month is left out of the learning once and judged on the stations not learnt
# from – the model knows neither the month nor the place, only the weather of the hour.

import collections, datetime, json, math, os, subprocess, sys, time
import numpy as np
from sklearn.ensemble import HistGradientBoostingRegressor

STATIONS = ['01975', '03379', '01420', '02932', '02667', '03631', '00183', '01684', '05705',
            '02290', '02712', '01358', '04271', '01048', '03668', '01443', '05906', '05792']
CACHE = sys.argv[1] if len(sys.argv) > 1 else 'sunshine-cache'
FIRST = datetime.date.fromisoformat(sys.argv[2] if len(sys.argv) > 2 else '2026-02-20')
LAST = datetime.date.fromisoformat(sys.argv[3] if len(sys.argv) > 3 else '2026-10-09')
RESOURCES = os.path.join(os.path.dirname(__file__), '..', 'app/src/main/resources/dev/nimbus/weather/data/remote')
MODEL = os.path.join(RESOURCES, 'sunshine_model.json')
FEATURES = ['sunshine', 'direct', 'global', 'diffuse', 'elevation', 'low']
FORECAST_MODEL = os.path.join(RESOURCES, 'forecast_sunshine_model.json')
FORECAST_FEATURES = ['sunshine', 'direct', 'elevation', 'cloud', 'precipitation']
FORECAST_FIELDS = 'sunshine_duration,direct_normal_irradiance,shortwave_radiation,diffuse_radiation,cloud_cover,precipitation'
PARAMS = dict(max_iter=100, learning_rate=0.1, max_leaf_nodes=15, min_samples_leaf=60,
              l2_regularization=1.0, early_stopping=False)


def get(url):
    for _ in range(4):
        try:
            return json.loads(subprocess.check_output(['curl', '-s', '--max-time', '120', url]))
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


def features(hour_end, lat, lon, sd, dni, ghi, dif, low):
    """As SatelliteSunshine.features: the hour's sun heights in six steps, clear sky by Meinel and Haurwitz."""
    els = [sun_altitude(hour_end - (5 + 10 * k) * 60, lat, lon) for k in range(6)]
    if max(els) <= 0:
        return None
    clear_dni = sum(0.0 if e <= 0.01 else 1367 * 0.7 ** ((1 / math.sin(e)) ** 0.678) for e in els) / 6
    clear_ghi = sum(0.0 if e <= 0.01 else 1098 * math.sin(e) * math.exp(-0.057 / math.sin(e)) for e in els) / 6
    return [sd / 60, dni / clear_dni if clear_dni > 0 else 0.0, ghi / clear_ghi if clear_ghi > 0 else 0.0,
            dif / ghi if ghi > 0 else 1.0, math.degrees(sum(els) / 6), np.nan if low is None else low], clear_dni


def low_cloud(h):
    """Model low cloud (%) of the hour ending at t: the mean of t − 1 h and t."""
    v = dict(zip(h['time'], h['cloud_cover_low']))
    return lambda t: (lambda a, b: None if a is None or b is None else (a + b) / 2)(v.get(t - 3600), v.get(t))


def dataset(low_name='low', model='best_match'):
    X, y, sid_of, t_of, clear = [], [], [], [], []
    for sid in STATIONS:
        b = cached(f'bs_{sid}.json', lambda: station(sid))
        lat, lon = b['src']['lat'], b['src']['lon']
        sat = cached(f'sat_{sid}.json', lambda: get(
            'https://satellite-api.open-meteo.com/v1/archive'
            f'?latitude={lat}&longitude={lon}&hourly=sunshine_duration,direct_normal_irradiance,shortwave_radiation,diffuse_radiation'
            f'&models=dwd_sis_europe_africa_v4&start_date={FIRST}&end_date={LAST}&timeformat=unixtime'))['hourly']
        low = low_cloud(cached(f'{low_name}_{sid}.json', lambda: get(
            'https://historical-forecast-api.open-meteo.com/v1/forecast'
            f'?latitude={lat}&longitude={lon}&start_date={FIRST}&end_date={LAST}&hourly=cloud_cover_low'
            f'&models={model}&timezone=UTC&timeformat=unixtime'))['hourly'])
        measured = {int(datetime.datetime.fromisoformat(x['t']).timestamp()): x['sun'] for x in b['recs']}
        for t, sd, dni, ghi, dif in zip(sat['time'], sat['sunshine_duration'], sat['direct_normal_irradiance'],
                                        sat['shortwave_radiation'], sat['diffuse_radiation']):
            if None in (sd, dni, ghi, dif) or measured.get(t) is None:
                continue
            f = features(t, lat, lon, sd, dni, ghi, dif, low(t))
            if f:
                X.append(f[0]); clear.append(f[1]); y.append(measured[t]); sid_of.append(sid); t_of.append(t)
    return np.array(X), np.array(y, float), np.array(sid_of), np.array(t_of), np.array(clear)


def forecast_dataset(model='best_match'):
    """The model's own hours (as the app gets them) against the stations: as ForecastSunshine.features."""
    X, y, sid_of, t_of = [], [], [], []
    for sid in STATIONS:
        b = cached(f'bs_{sid}.json', lambda: station(sid))
        lat, lon = b['src']['lat'], b['src']['lon']
        h = cached(f'fc_{model}_{sid}.json', lambda: get(
            'https://historical-forecast-api.open-meteo.com/v1/forecast'
            f'?latitude={lat}&longitude={lon}&start_date={FIRST}&end_date={LAST}&hourly={FORECAST_FIELDS}'
            f'&models={model}&timezone=UTC&timeformat=unixtime'))['hourly']
        cloud = dict(zip(h['time'], h['cloud_cover']))
        measured = {int(datetime.datetime.fromisoformat(x['t']).timestamp()): x['sun'] for x in b['recs']}
        for t, sd, dni, pr in zip(h['time'], h['sunshine_duration'], h['direct_normal_irradiance'], h['precipitation']):
            if None in (sd, dni) or measured.get(t) is None:
                continue
            f = forecast_features(t, lat, lon, sd, dni, cloud.get(t - 3600), cloud.get(t), pr)
            if f:
                X.append(f); y.append(measured[t]); sid_of.append(sid); t_of.append(t)
    return np.array(X), np.array(y, float), np.array(sid_of), np.array(t_of)


def forecast_features(hour_end, lat, lon, sd, dni, cloud_start, cloud_end, precipitation):
    """As ForecastSunshine.features: the model's sunshine, its direct beam against a clear sky's, the sun's height, cloud, precipitation."""
    els = [sun_altitude(hour_end - (5 + 10 * k) * 60, lat, lon) for k in range(6)]
    if max(els) <= 0:
        return None
    clear_dni = sum(0.0 if e <= 0.01 else 1367 * 0.7 ** ((1 / math.sin(e)) ** 0.678) for e in els) / 6
    cloud = np.nan if cloud_start is None or cloud_end is None else (cloud_start + cloud_end) / 2
    return [sd / 60, dni / clear_dni if clear_dni > 0 else 0.0, math.degrees(sum(els) / 6), cloud,
            np.nan if precipitation is None else precipitation]


def check(X, y, sids, ts, raw, label):
    """Every month left out of the learning once, judged on the stations not learnt from."""
    months = np.array([datetime.datetime.fromtimestamp(t, datetime.UTC).month for t in ts])
    fit = np.isin(sids, STATIONS[0::2])
    rows = collections.defaultdict(list)
    for mo in sorted(set(months)):
        learn, judge = fit & (months != mo), ~fit & (months == mo)
        model = HistGradientBoostingRegressor(**PARAMS).fit(X[learn], y[learn])
        for name, p in list(raw(X[judge], judge).items()) + [('model', model.predict(X[judge]))]:
            rows[name].append((errors(p, y[judge], sids[judge], ts[judge]), judge.sum()))
        print(f'{label} month {mo:2d}: ' + ' | '.join(f'{n} bias {r[-1][0][1]:+5.2f}' for n, r in rows.items()))
    for name, r in rows.items():
        n = sum(k for _, k in r)
        h, b, d = (sum(e[i] * k for e, k in r) / n for i in range(3))
        print(f'{label} {name:10} (month left out, other stations): hour {h:5.2f} min, bias {b:+5.2f} min/h, day {d:4.0f} min')
    return fit


def write(X, y, features, path):
    final = HistGradientBoostingRegressor(**PARAMS).fit(X, y)
    m = export(final, features)
    gap = max(abs(evaluate(m, X[i]) - min(60, max(0, final.predict(X[i:i + 1])[0]))) for i in range(0, len(X), 97))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(m, open(path, 'w'), separators=(',', ':'))
    print(f'model written: {os.path.normpath(path)} ({os.path.getsize(path) // 1024} KB), exported vs learnt: {gap:.4f} min')


def share_07(X, clear):
    """The share of the direct irradiance: 60 × DNI / (0.7 × clear DNI); Open-Meteo's value where 0.7 × clear DNI < 120 W/m²."""
    return np.where(0.7 * clear < 120, X[:, 0], np.clip(60 * X[:, 1] / 0.7, 0, 60))


def errors(p, y, sids, ts):
    p = np.clip(p, 0, 60)
    sel = (p > 0) | (y > 0)
    days = collections.defaultdict(lambda: [0.0, 0.0])
    for a, b, s, t in zip(p, y, sids, ts):
        days[(s, t // 86400)][0] += a
        days[(s, t // 86400)][1] += b
    return np.abs(p - y)[sel].mean(), (p - y)[sel].mean(), np.mean([abs(a - b) for a, b in days.values()])


def export(model, features):
    trees = []
    for (pred,) in model._predictors:
        n = pred.nodes
        trees.append({'f': [int(x) for x in n['feature_idx']], 't': [float('%.9g' % x) for x in n['num_threshold']],
                      'l': [int(x) if not leaf else -1 for x, leaf in zip(n['left'], n['is_leaf'])],
                      'r': [int(x) for x in n['right']], 'm': [int(x) for x in n['missing_go_to_left']],
                      'v': [round(float(x), 7) for x in n['value']]})
    return {'features': features, 'baseline': round(float(model._baseline_prediction.ravel()[0]), 7), 'trees': trees}


def evaluate(m, x):
    total = m['baseline']
    for tr in m['trees']:
        i = 0
        while tr['l'][i] >= 0:
            v = x[tr['f'][i]]
            left = tr['m'][i] == 1 if math.isnan(v) else v <= tr['t'][i]
            i = tr['l'][i] if left else tr['r'][i]
        total += tr['v'][i]
    return min(60.0, max(0.0, total))


def main():
    X, y, sids, ts, clear = dataset()
    print(f'satellite: {len(y)} daylight hours at {len(STATIONS)} stations, {FIRST} – {LAST}')
    fit = check(X, y, sids, ts, lambda x, judge: {'Open-Meteo': x[:, 0], 'share 0.7': share_07(x, clear[judge])}, 'satellite')
    # the low cloud of another model than the one learnt from (ICON chosen in the settings)
    Xi, yi, si, ti, _ = dataset('lowicon', 'icon_seamless')
    judge = ~np.isin(si, STATIONS[0::2])
    learnt = HistGradientBoostingRegressor(**PARAMS).fit(X[fit], y[fit])
    print('satellite, ICON low cloud, other stations: hour %5.2f min, bias %+5.2f, day %4.0f' % errors(learnt.predict(Xi[judge]), yi[judge], si[judge], ti[judge]))
    write(X, y, FEATURES, MODEL)

    X, y, sids, ts = forecast_dataset()
    print(f'forecast: {len(y)} daylight hours')
    fit = check(X, y, sids, ts, lambda x, judge: {'Open-Meteo': x[:, 0]}, 'forecast')
    Xi, yi, si, ti = forecast_dataset('icon_seamless')
    judge = ~np.isin(si, STATIONS[0::2])
    learnt = HistGradientBoostingRegressor(**PARAMS).fit(X[fit], y[fit])
    print('forecast, ICON, other stations: Open-Meteo hour %5.2f min, bias %+5.2f, day %4.0f' % errors(Xi[judge, 0], yi[judge], si[judge], ti[judge]))
    print('forecast, ICON, other stations: model      hour %5.2f min, bias %+5.2f, day %4.0f' % errors(learnt.predict(Xi[judge]), yi[judge], si[judge], ti[judge]))
    write(X, y, FORECAST_FEATURES, FORECAST_MODEL)


if __name__ == '__main__':
    main()
