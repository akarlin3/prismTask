package com.averycorp.prismtask.domain.anchor

import com.averycorp.prismtask.domain.model.ExternalAnchor
import com.averycorp.prismtask.domain.model.NumericOp
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Gson round-trip for the [ExternalAnchor] sealed hierarchy. Mirrors
 * the polymorphic-by-`type`-string discriminator pattern used by
 * `AutomationJsonAdapter` so storage is a single `anchor_json` TEXT
 * column on `external_anchors` with no union-type schema gymnastics.
 *
 * `decode` returns null on malformed JSON rather than throwing — the
 * UI treats null as a corrupted anchor row and surfaces it as a
 * placeholder rather than crashing the project-roadmap screen.
 */
object ExternalAnchorJsonAdapter {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ExternalAnchor::class.java, AnchorAdapter)
        .create()

    fun encode(anchor: ExternalAnchor): String =
        gson.toJson(anchor, ExternalAnchor::class.java)

    fun decode(json: String): ExternalAnchor? = runCatching {
        gson.fromJson(json, ExternalAnchor::class.java)
    }.getOrNull()

    private object AnchorAdapter :
        JsonSerializer<ExternalAnchor>,
        JsonDeserializer<ExternalAnchor> {
        override fun serialize(
            src: ExternalAnchor,
            typeOfSrc: Type,
            context: JsonSerializationContext?
        ): JsonElement = JsonObject().apply {
            addProperty("type", src.type)
            when (src) {
                is ExternalAnchor.CalendarDeadline -> addProperty("epochMs", src.epochMs)
                is ExternalAnchor.NumericThreshold -> {
                    addProperty("metric", src.metric)
                    addProperty("op", src.op.name)
                    addProperty("value", src.value)
                }
                is ExternalAnchor.BooleanGate -> {
                    addProperty("gateKey", src.gateKey)
                    addProperty("expectedState", src.expectedState)
                }
            }
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext?
        ): ExternalAnchor {
            val obj = json.asJsonObject
            return when (val t = obj.get("type").asString) {
                ExternalAnchor.CalendarDeadline.TYPE ->
                    ExternalAnchor.CalendarDeadline(obj.get("epochMs").asLong)
                ExternalAnchor.NumericThreshold.TYPE ->
                    ExternalAnchor.NumericThreshold(
                        metric = obj.get("metric").asString,
                        op = NumericOp.valueOf(obj.get("op").asString),
                        value = obj.get("value").asDouble
                    )
                ExternalAnchor.BooleanGate.TYPE ->
                    ExternalAnchor.BooleanGate(
                        gateKey = obj.get("gateKey").asString,
                        expectedState = obj.get("expectedState").asBoolean
                    )
                else -> throw IllegalArgumentException("unknown anchor type: $t")
            }
        }
    }
}
