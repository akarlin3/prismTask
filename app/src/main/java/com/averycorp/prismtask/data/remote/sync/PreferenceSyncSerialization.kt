package com.averycorp.prismtask.data.remote.sync

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * Serializes DataStore [Preferences] to/from Firestore-compatible maps.
 *
 * Firestore doc layout per synced preference file:
 *
 * ```
 * /users/{uid}/prefs/{docName} = {
 *   "__pref_updated_at": 1712345678901,        // client-wall-clock on push
 *   "__pref_device_id":  "uuid-of-pushing-device",
 *   "__pref_types": {
 *     "theme_mode": "string",
 *     "font_scale": "float",
 *     "recent_colors": "stringSet",
 *     ...
 *   },
 *   "theme_mode":   "dark",
 *   "font_scale":   1.25,
 *   "recent_colors": ["#AABBCC", "#112233"],
 *   ...
 * }
 * ```
 *
 * Type tags let the pull side reconstruct the correct `Preferences.Key<T>`
 * without the remote needing any knowledge of the local preference class.
 */
internal object PreferenceSyncSerialization {
    const val META_UPDATED_AT = "__pref_updated_at"
    const val META_DEVICE_ID = "__pref_device_id"
    const val META_TYPES = "__pref_types"

    /** Keys whose NAME matches any of these are never pushed. */
    private val ALWAYS_EXCLUDED_PREFIXES = listOf("__pref_")

    private const val TYPE_BOOL = "bool"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_DOUBLE = "double"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "stringSet"

    /**
     * Builds the Firestore payload for one preference file. Returns null
     * when nothing is syncable (empty file or all keys excluded).
     */
    fun buildPayload(
        prefs: Preferences,
        excludeKeys: Set<String>,
        deviceId: String,
        nowMs: Long
    ): Map<String, Any>? {
        val values = mutableMapOf<String, Any>()
        val types = mutableMapOf<String, String>()
        for ((key, value) in prefs.asMap()) {
            val name = key.name
            if (name in excludeKeys) continue
            if (ALWAYS_EXCLUDED_PREFIXES.any { name.startsWith(it) }) continue
            val encoded = encodeValue(value) ?: continue
            values[name] = encoded.first
            types[name] = encoded.second
        }
        if (values.isEmpty()) return null
        return buildMap {
            putAll(values)
            put(META_TYPES, types)
            put(META_UPDATED_AT, nowMs)
            put(META_DEVICE_ID, deviceId)
        }
    }

    /**
     * Applies a remote Firestore snapshot into [out]. Keys listed in
     * [excludeKeys] are ignored so device-local values are never clobbered
     * by stale remote state.
     */
    fun applyRemote(
        out: MutablePreferences,
        remote: Map<String, Any?>,
        excludeKeys: Set<String>
    ): Int {
        @Suppress("UNCHECKED_CAST")
        val types = remote[META_TYPES] as? Map<String, String> ?: return 0
        var applied = 0
        for ((name, type) in types) {
            if (name in excludeKeys) continue
            if (ALWAYS_EXCLUDED_PREFIXES.any { name.startsWith(it) }) continue
            val raw = remote[name] ?: continue
            if (applyTyped(out, name, type, raw)) applied++
        }
        return applied
    }

    private fun encodeValue(value: Any): Pair<Any, String>? = when (value) {
        is Boolean -> value to TYPE_BOOL
        is Int -> value.toLong() to TYPE_INT
        is Long -> value to TYPE_LONG
        is Float -> value.toDouble() to TYPE_FLOAT
        is Double -> value to TYPE_DOUBLE
        is String -> value to TYPE_STRING
        is Set<*> -> value.filterIsInstance<String>().toList() to TYPE_STRING_SET
        else -> null
    }

    private fun applyTyped(
        out: MutablePreferences,
        name: String,
        type: String,
        raw: Any
    ): Boolean = runCatching {
        when (type) {
            TYPE_BOOL -> {
                val v = raw as? Boolean ?: return@runCatching false
                out[booleanPreferencesKey(name)] = v
                true
            }
            TYPE_INT -> {
                val v = (raw as? Number)?.toInt() ?: return@runCatching false
                out[intPreferencesKey(name)] = v
                true
            }
            TYPE_LONG -> {
                val v = (raw as? Number)?.toLong() ?: return@runCatching false
                out[longPreferencesKey(name)] = v
                true
            }
            TYPE_FLOAT -> {
                val v = (raw as? Number)?.toFloat() ?: return@runCatching false
                out[floatPreferencesKey(name)] = v
                true
            }
            TYPE_DOUBLE -> {
                val v = (raw as? Number)?.toDouble() ?: return@runCatching false
                out[doublePreferencesKey(name)] = v
                true
            }
            TYPE_STRING -> {
                val v = raw as? String ?: return@runCatching false
                out[stringPreferencesKey(name)] = v
                true
            }
            TYPE_STRING_SET -> {
                @Suppress("UNCHECKED_CAST")
                val v = (raw as? List<*>)?.filterIsInstance<String>()?.toSet()
                    ?: return@runCatching false
                out[stringSetPreferencesKey(name)] = v
                true
            }
            else -> false
        }
    }.getOrDefault(false)

    /**
     * Snapshot hash used to detect whether local state has changed since the
     * last push. Cheap and order-stable — we sort keys before hashing.
     */
    fun fingerprint(
        prefs: Preferences,
        excludeKeys: Set<String>
    ): Int {
        val entries = prefs.asMap()
            .filter { (k, _) ->
                val name = k.name
                name !in excludeKeys && ALWAYS_EXCLUDED_PREFIXES.none { name.startsWith(it) }
            }
            .entries
            .sortedBy { it.key.name }
        var h = 1
        for ((k, v) in entries) {
            h = 31 * h + k.name.hashCode()
            h = 31 * h + valueHash(v)
        }
        return h
    }

    private fun valueHash(value: Any): Int = when (value) {
        is Set<*> -> value.filterIsInstance<String>().sorted().hashCode()
        else -> value.hashCode()
    }
}
