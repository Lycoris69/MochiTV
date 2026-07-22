package com.lycoris.mochitv.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room [TypeConverter] that serialises [List]<[String]> ↔ JSON string using Gson.
 *
 * Registered on the database class via `@TypeConverters(Converters::class)`.
 * Room discovers the annotated methods automatically and uses them whenever
 * an entity field of type `List<String>` is read from or written to the database.
 */
class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
