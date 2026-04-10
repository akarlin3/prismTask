# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Room entities & DAOs ──
-keep class com.averycorp.averytask.data.local.entity.** { *; }
-keep interface com.averycorp.averytask.data.local.dao.** { *; }
-keep class com.averycorp.averytask.data.local.dao.ProjectWithCount { *; }
-keep class com.averycorp.averytask.data.local.dao.EntityFrequency { *; }

# ── Room relation / cross-ref classes ──
-keep class com.averycorp.averytask.data.local.entity.TaskTagCrossRef { *; }
-keep class com.averycorp.averytask.data.local.entity.TaskWithTags { *; }

# ── Domain models (serialized with Gson) ──
-keep class com.averycorp.averytask.domain.model.** { *; }

# ── Room type converters ──
-keep class com.averycorp.averytask.data.local.converter.** { *; }

# ── Notification receivers ──
-keep class com.averycorp.averytask.notifications.** { *; }

# ── WorkManager workers ──
-keep class com.averycorp.averytask.workers.** { *; }

# ── Firebase / Firestore ──
-keep class com.averycorp.averytask.data.remote.model.** { *; }
-keep class com.averycorp.averytask.data.remote.mapper.** { *; }

# ── Update checker (deserialized by Gson) ──
-keep class com.averycorp.averytask.data.remote.VersionInfo { *; }

# ── Claude API models (inner classes in ClaudeParserService, deserialized by Gson) ──
-keep class com.averycorp.averytask.data.remote.ClaudeParserService$* { *; }

# ── gRPC (transitive dependency from Firebase/Firestore) ──
-dontwarn io.grpc.internal.**
-dontwarn io.grpc.**

# ── Google Drive API ──
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.api.services.**

# ── Google Play Billing ──
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# ── Google Calendar API ──
-keep class com.google.api.services.calendar.** { *; }

# ── Billing data classes ──
-keep class com.averycorp.averytask.data.billing.** { *; }

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
