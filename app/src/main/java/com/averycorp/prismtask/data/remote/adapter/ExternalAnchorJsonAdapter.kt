package com.averycorp.prismtask.data.remote.adapter

import com.averycorp.prismtask.domain.model.ComparisonOp
import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Polymorphic Gson round-trip for the [ExternalAnchor] sealed
 * hierarchy. Mirrors the pattern in
 * `domain/automation/AutomationJsonAdapter.kt` from PR #1056: a single
 * "type" discriminator field selects the variant on decode, and the
 * encoder writes it on every variant.
 *
 * On decode, a malformed payload returns `null` rather than throwing —
 * upstream callers (the SyncMapper, the ProjectRepository) treat null
 * as "drop the anchor" so a corrupted-row pull doesn't abort an entire
 * project sync.
 */
object ExternalAnchorJsonAdapter {

    private const val TYPE_CALENDAR = "calendar_deadline"
    private const val TYPE_NUMERIC = "numeric_threshold"
    private const val TYPE_BOOLEAN = "boolean_gate"

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ExternalAnchor::class.java, AnchorAdapter)
        .create()

    fun encode(anchor: ExternalAnchor): String = gson.toJson(anchor, ExternalAnchor::class.java)

    fun decode(json: String?): ExternalAnchor? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, ExternalAnchor::class.java) }.getOrNull()
    }

    private object AnchorAdapter :
        JsonSerializer<ExternalAnchor>,
        JsonDeserializer<ExternalAnchor> {

        override fun serialize(
            src: ExternalAnchor,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val obj = JsonObject()
            when (src) {
                is ExternalAnchor.CalendarDeadline -> {
                    obj.add("type", JsonPrimitive(TYPE_CALENDAR))
                    obj.add("epochMs", JsonPrimitive(src.epochMs))
                }
                is ExternalAnchor.NumericThreshold -> {
                    obj.add("type", JsonPrimitive(TYPE_NUMERIC))
                    obj.add("metric", JsonPrimitive(src.metric))
                    obj.add("op", JsonPrimitive(src.op.symbol))
                    obj.add("value", JsonPrimitive(src.value))
                }
                is ExternalAnchor.BooleanGate -> {
                    obj.add("type", JsonPrimitive(TYPE_BOOLEAN))
                    obj.add("gateKey", JsonPrimitive(src.gateKey))
                    obj.add("expectedState", JsonPrimitive(src.expectedState))
                }
            }
            return obj
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ExternalAnchor? {
            val obj = json.asJsonObject
            return when (val t = obj.get("type")?.asString) {
                TYPE_CALENDAR -> {
                    val epochMs = obj.get("epochMs")?.asLong ?: return null
                    ExternalAnchor.CalendarDeadline(epochMs)
                }
                TYPE_NUMERIC -> {
                    val metric = obj.get("metric")?.asString ?: return null
                    val opSymbol = obj.get("op")?.asString
                    val op = ComparisonOp.fromSymbol(opSymbol) ?: return null
                    val value = obj.get("value")?.asDouble ?: return null
                    ExternalAnchor.NumericThreshold(metric, op, value)
                }
                TYPE_BOOLEAN -> {
                    val gateKey = obj.get("gateKey")?.asString ?: return null
                    val expectedState = obj.get("expectedState")?.asBoolean ?: return null
                    ExternalAnchor.BooleanGate(gateKey, expectedState)
                }
                else -> {
                    // Forward-compat: unknown discriminator silently drops
                    // the row rather than crashing the pull / decode path.
                    @Suppress("UNUSED_VARIABLE") val unused = t
                    null
                }
            }
        }
    }
}
