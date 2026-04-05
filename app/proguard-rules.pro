# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Room entities & DAOs ──
-keep class com.averykarlin.averytask.data.local.entity.** { *; }
-keep interface com.averykarlin.averytask.data.local.dao.** { *; }
-keep class com.averykarlin.averytask.data.local.dao.ProjectWithCount { *; }

# ── Domain models (serialized with Gson) ──
-keep class com.averykarlin.averytask.domain.model.** { *; }

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Preserve generic type info for Gson reflection
-keepattributes EnclosingMethod
-keepattributes InnerClasses
