package com.pfe.gestionetudiantmobile.data.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LocalDateAdapter : TypeAdapter<LocalDate>() {
    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(input: JsonReader): LocalDate? {
        if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            return null
        }
        return LocalDate.parse(input.nextString())
    }
}

class LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
    override fun write(out: JsonWriter, value: LocalDateTime?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(input: JsonReader): LocalDateTime? {
        if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            return null
        }
        return LocalDateTime.parse(input.nextString())
    }
}

class LocalTimeAdapter : TypeAdapter<LocalTime>() {
    override fun write(out: JsonWriter, value: LocalTime?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value.toString())
        }
    }

    override fun read(input: JsonReader): LocalTime? {
        if (input.peek() == JsonToken.NULL) {
            input.nextNull()
            return null
        }
        return LocalTime.parse(input.nextString())
    }
}
