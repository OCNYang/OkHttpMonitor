package com.ocnyang.ktormonitor.internal

import io.ktor.http.Headers

/**
 * Converts Ktor headers to a "Name: Value" string for storage/display.
 */
internal fun Headers.toHeaderString(): String {
    val sb = StringBuilder()
    entries().forEach { (name, values) ->
        values.forEach { value ->
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(name).append(": ").append(value)
        }
    }
    return sb.toString()
}

/**
 * Returns the total byte count of all headers (approximation: name + ": " + value + "\r\n").
 */
internal fun Headers.byteCount(): Long {
    var count = 0L
    entries().forEach { (name, values) ->
        values.forEach { value ->
            count += name.length + 4L + value.length // "name: value\r\n"
        }
    }
    return count
}

/**
 * Returns a new headers map with [names] replaced by "**".
 */
internal fun Headers.redact(names: Set<String>): Headers {
    if (names.isEmpty()) return this
    val builder = io.ktor.http.HeadersBuilder()
    entries().forEach { (name, values) ->
        if (names.any { it.equals(name, ignoreCase = true) }) {
            builder.append(name, "**")
        } else {
            values.forEach { builder.append(name, it) }
        }
    }
    return builder.build()
}
