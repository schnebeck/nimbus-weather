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
