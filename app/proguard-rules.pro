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
-keep class com.averykarlin.averytask.data.local.dao.EntityFrequency { *; }

# ── Room relation / cross-ref classes ──
-keep class com.averykarlin.averytask.data.local.entity.TaskTagCrossRef { *; }
-keep class com.averykarlin.averytask.data.local.entity.TaskWithTags { *; }

# ── Domain models (serialized with Gson) ──
-keep class com.averykarlin.averytask.domain.model.** { *; }

# ── Room type converters ──
-keep class com.averykarlin.averytask.data.local.converter.** { *; }

# ── Notification receivers ──
-keep class com.averykarlin.averytask.notifications.** { *; }

# ── WorkManager workers ──
-keep class com.averykarlin.averytask.workers.** { *; }

# ── Firebase / Firestore ──
-keep class com.averykarlin.averytask.data.remote.model.** { *; }
-keep class com.averykarlin.averytask.data.remote.mapper.** { *; }

# ── Claude API models (inner classes in ClaudeParserService, deserialized by Gson) ──
-keep class com.averykarlin.averytask.data.remote.ClaudeParserService$* { *; }

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
