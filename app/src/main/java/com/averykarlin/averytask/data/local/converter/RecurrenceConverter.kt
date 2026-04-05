package com.averykarlin.averytask.data.local.converter

import com.averykarlin.averytask.domain.model.RecurrenceRule
import com.google.gson.Gson

object RecurrenceConverter {

    private val gson = Gson()

    fun toJson(rule: RecurrenceRule): String = gson.toJson(rule)

    fun fromJson(json: String): RecurrenceRule? =
        try {
            gson.fromJson(json, RecurrenceRule::class.java)
        } catch (e: Exception) {
            null
        }
}
