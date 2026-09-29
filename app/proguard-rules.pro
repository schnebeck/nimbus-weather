# Nimbus - app/proguard-rules.pro
# Shrinker rules for the release build.
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

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.nimbus.weather.**$$serializer { *; }
-keepclassmembers class dev.nimbus.weather.** { *** Companion; }
-keepclasseswithmembers class dev.nimbus.weather.** { kotlinx.serialization.KSerializer serializer(...); }

# MapLibre (consumer rules ship with the AAR; keep native bridge just in case)
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
