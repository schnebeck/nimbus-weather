<!--
  Nimbus - docs/fdroid/README.md
  How to submit Nimbus to F-Droid.

    Copyright (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
    Produced by Thorsten Schnebeck - the idea, the decisions, the testing.
    Written by Anthropic Claude Opus 5.5 - AI generated content.

  SPDX-FileCopyrightText: (C) 2026 Thorsten Schnebeck <thorsten.schnebeck@gmx.net>
  SPDX-FileContributor: Anthropic Claude Opus 5.5 (AI generated content)
  SPDX-License-Identifier: GPL-3.0-or-later
-->

# F-Droid

- `dev.nimbus.weather.yml` – build recipe for the fdroiddata repository, already in the format of
  `fdroid rewritemeta` and checked with `fdroid lint`. It builds the tag v1.10.2 (commit hash).
- Store texts, screenshots, icon, banner and changelogs are read by F-Droid from
  `fastlane/metadata/android/` in this repository (en-US and de-DE).

## Submitting

1. Fork https://gitlab.com/fdroid/fdroiddata and add the recipe as `metadata/dev.nimbus.weather.yml`.
2. Open a merge request with the "App inclusion" template.
3. Later releases are picked up automatically from the Git tags (`UpdateCheckMode: Tags`); every
   release needs a changelog file `fastlane/metadata/android/*/changelogs/<versionCode>.txt`.
