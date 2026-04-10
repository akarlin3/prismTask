package com.averycorp.prismtask.data.export

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Overlays an imported [JsonObject] onto a freshly-constructed [default] entity and
 * returns the resulting entity.
 *
 * This is the core of forward/backwards-compatible entity import:
 *
 *  - Fields present in [imported] (including explicit nulls) override the defaults.
 *  - Fields missing from [imported] — e.g. new entity fields that didn't exist when
 *    the file was exported — keep the values from [default], which were produced by
 *    the Kotlin primary constructor. That's why callers pass a freshly built instance
 *    with only the required constructor arguments supplied.
 *  - Helper fields prefixed with `_` (used for foreign-key resolution like
 *    `_projectName`, `_tagNames`) are skipped; they are consumed by the caller
 *    separately.
 *
 * The net effect: adding a new field to an entity automatically roundtrips through
 * export/import, and old backups produced before that field existed still load
 * correctly, picking up the new default value.
 */
@PublishedApi
internal val ENTITY_MERGER_GSON: Gson = Gson()

internal inline fun <reified T : Any> mergeEntityWithDefaults(
    default: T,
    imported: JsonObject
): T {
    val defaultJson = ENTITY_MERGER_GSON.toJsonTree(default).asJsonObject
    imported.entrySet().forEach { (key, value) ->
        if (key.startsWith("_")) return@forEach
        defaultJson.add(key, value)
    }
    return ENTITY_MERGER_GSON.fromJson(defaultJson, T::class.java)
}
