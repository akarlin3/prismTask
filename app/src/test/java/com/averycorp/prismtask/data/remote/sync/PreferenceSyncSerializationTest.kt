package com.averycorp.prismtask.data.remote.sync

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for [PreferenceSyncSerialization]. Every supported DataStore
 * value type (Boolean, Int, Long, Float, Double, String, Set<String>) must
 * encode to a Firestore-compatible payload and decode back to the same value
 * without type drift.
 *
 * Pure-JVM — no Firestore, no Android context — because the serializer
 * operates on [androidx.datastore.preferences.core.Preferences] in memory.
 */
class PreferenceSyncSerializationTest {
    private val deviceId = "device-A"

    private fun prefs(block: MutablePreferences.() -> Unit): MutablePreferences =
        mutablePreferencesOf().apply(block)

    @Test
    fun buildPayload_roundTripsAllSupportedTypes() {
        val source = prefs {
            this[booleanPreferencesKey("flag")] = true
            this[intPreferencesKey("count")] = 42
            this[longPreferencesKey("stamp")] = 1_234_567_890L
            this[floatPreferencesKey("scale")] = 1.25f
            this[doublePreferencesKey("ratio")] = 3.14159
            this[stringPreferencesKey("label")] = "hello"
            this[stringSetPreferencesKey("tags")] = setOf("a", "b", "c")
        }

        val payload = PreferenceSyncSerialization.buildPayload(
            prefs = source,
            excludeKeys = emptySet(),
            deviceId = deviceId,
            nowMs = 1000L
        )
        assertTrue(payload != null)
        requireNotNull(payload)

        assertEquals(deviceId, payload[PreferenceSyncSerialization.META_DEVICE_ID])
        assertEquals(1000L, payload[PreferenceSyncSerialization.META_UPDATED_AT])

        val restored = mutablePreferencesOf()
        val applied = PreferenceSyncSerialization.applyRemote(
            out = restored,
            remote = payload,
            excludeKeys = emptySet()
        )
        assertEquals(7, applied)
        assertEquals(true, restored[booleanPreferencesKey("flag")])
        assertEquals(42, restored[intPreferencesKey("count")])
        assertEquals(1_234_567_890L, restored[longPreferencesKey("stamp")])
        assertEquals(1.25f, restored[floatPreferencesKey("scale")])
        assertEquals(3.14159, restored[doublePreferencesKey("ratio")])
        assertEquals("hello", restored[stringPreferencesKey("label")])
        assertEquals(setOf("a", "b", "c"), restored[stringSetPreferencesKey("tags")])
    }

    @Test
    fun excludedKeys_neverAppearInPayloadAndNeverApplied() {
        val source = prefs {
            this[stringPreferencesKey("public_label")] = "visible"
            this[stringPreferencesKey("device_only")] = "secret"
        }
        val payload = PreferenceSyncSerialization.buildPayload(
            prefs = source,
            excludeKeys = setOf("device_only"),
            deviceId = deviceId,
            nowMs = 1L
        )!!

        assertFalse(payload.containsKey("device_only"))
        assertTrue(payload.containsKey("public_label"))

        val remote = payload.toMutableMap<String, Any>()
        remote["device_only"] = "malicious"
        @Suppress("UNCHECKED_CAST")
        val types = (remote[PreferenceSyncSerialization.META_TYPES] as Map<String, String>)
            .toMutableMap()
        types["device_only"] = "string"
        remote[PreferenceSyncSerialization.META_TYPES] = types

        val restored = mutablePreferencesOf()
        val applied = PreferenceSyncSerialization.applyRemote(
            out = restored,
            remote = remote,
            excludeKeys = setOf("device_only")
        )
        assertEquals(1, applied)
        assertEquals("visible", restored[stringPreferencesKey("public_label")])
        assertNull(restored[stringPreferencesKey("device_only")])
    }

    @Test
    fun metaKeys_areNeverLeakedIntoPayload() {
        val source = prefs {
            this[stringPreferencesKey("__pref_device_id")] = "stale"
            this[stringPreferencesKey("theme_mode")] = "dark"
        }
        val payload = PreferenceSyncSerialization.buildPayload(
            prefs = source,
            excludeKeys = emptySet(),
            deviceId = deviceId,
            nowMs = 1L
        )!!

        // __pref_device_id in the payload is the sync service's own device id,
        // never the stale value that happened to live under that key locally.
        assertEquals(deviceId, payload[PreferenceSyncSerialization.META_DEVICE_ID])
        @Suppress("UNCHECKED_CAST")
        val types = payload[PreferenceSyncSerialization.META_TYPES] as Map<String, String>
        assertFalse(types.containsKey("__pref_device_id"))
    }

    @Test
    fun buildPayload_returnsNullWhenNothingSyncable() {
        val source = prefs {
            this[stringPreferencesKey("only_excluded")] = "x"
        }
        val payload = PreferenceSyncSerialization.buildPayload(
            prefs = source,
            excludeKeys = setOf("only_excluded"),
            deviceId = deviceId,
            nowMs = 1L
        )
        assertNull(payload)
    }

    @Test
    fun fingerprint_isOrderInsensitiveButValueSensitive() {
        val a = prefs {
            this[stringPreferencesKey("a")] = "1"
            this[stringPreferencesKey("b")] = "2"
        }
        val b = prefs {
            this[stringPreferencesKey("b")] = "2"
            this[stringPreferencesKey("a")] = "1"
        }
        val c = prefs {
            this[stringPreferencesKey("a")] = "1"
            this[stringPreferencesKey("b")] = "3"
        }
        assertEquals(
            "fingerprint ignores insertion order",
            PreferenceSyncSerialization.fingerprint(a, emptySet()),
            PreferenceSyncSerialization.fingerprint(b, emptySet())
        )
        assertNotEquals(
            "fingerprint tracks value changes",
            PreferenceSyncSerialization.fingerprint(a, emptySet()),
            PreferenceSyncSerialization.fingerprint(c, emptySet())
        )
    }

    @Test
    fun fingerprint_isStableForSetValues_regardlessOfIterationOrder() {
        val a = prefs {
            this[stringSetPreferencesKey("tags")] = setOf("x", "y", "z")
        }
        val b = prefs {
            this[stringSetPreferencesKey("tags")] = setOf("z", "y", "x")
        }
        assertEquals(
            PreferenceSyncSerialization.fingerprint(a, emptySet()),
            PreferenceSyncSerialization.fingerprint(b, emptySet())
        )
    }

    @Test
    fun applyRemote_typeMismatchDoesNotCrashOrWriteBadValue() {
        val remote = mapOf(
            PreferenceSyncSerialization.META_TYPES to mapOf("count" to "int"),
            PreferenceSyncSerialization.META_DEVICE_ID to "other",
            "count" to "not-a-number"
        )
        val restored = mutablePreferencesOf()
        val applied = PreferenceSyncSerialization.applyRemote(
            out = restored,
            remote = remote,
            excludeKeys = emptySet()
        )
        assertEquals(0, applied)
        assertNull(restored[intPreferencesKey("count")])
    }

    @Test
    fun intAndLong_surviveFirestoreNumberWidening() {
        // Firestore hands numbers back as java.lang.Long regardless of the
        // type we pushed. Both int and long keys must tolerate that.
        val remote = mapOf(
            PreferenceSyncSerialization.META_TYPES to mapOf(
                "as_int" to "int",
                "as_long" to "long"
            ),
            PreferenceSyncSerialization.META_DEVICE_ID to "other",
            "as_int" to 100L,
            "as_long" to 200L
        )
        val restored = mutablePreferencesOf()
        val applied = PreferenceSyncSerialization.applyRemote(
            out = restored,
            remote = remote,
            excludeKeys = emptySet()
        )
        assertEquals(2, applied)
        assertEquals(100, restored[intPreferencesKey("as_int")])
        assertEquals(200L, restored[longPreferencesKey("as_long")])
    }

    @Test
    fun floatAndDouble_surviveFirestoreNumberWidening() {
        val remote = mapOf(
            PreferenceSyncSerialization.META_TYPES to mapOf(
                "as_float" to "float",
                "as_double" to "double"
            ),
            PreferenceSyncSerialization.META_DEVICE_ID to "other",
            "as_float" to 1.5,
            "as_double" to 2.5
        )
        val restored = mutablePreferencesOf()
        val applied = PreferenceSyncSerialization.applyRemote(
            out = restored,
            remote = remote,
            excludeKeys = emptySet()
        )
        assertEquals(2, applied)
        assertEquals(1.5f, restored[floatPreferencesKey("as_float")])
        assertEquals(2.5, restored[doublePreferencesKey("as_double")])
    }
}
