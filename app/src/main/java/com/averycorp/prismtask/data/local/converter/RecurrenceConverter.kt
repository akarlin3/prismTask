package com.averycorp.prismtask.data.local.converter

import com.averycorp.prismtask.domain.model.RecurrenceRule
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
