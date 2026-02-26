package com.ocnyang.okhttpmonitor.internal

import okhttp3.Headers

/**
 * Converts headers to a simple string representation for storage.
 * Each header is on its own line in "Name: Value" format.
 */
internal fun Headers.toHeaderString(): String {
    val sb = StringBuilder()
    for (i in 0 until size) {
        if (sb.isNotEmpty()) sb.append('\n')
        sb.append(name(i)).append(": ").append(value(i))
    }
    return sb.toString()
}
