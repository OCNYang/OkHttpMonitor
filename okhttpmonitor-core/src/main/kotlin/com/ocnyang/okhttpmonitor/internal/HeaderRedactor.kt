package com.ocnyang.okhttpmonitor.internal

import okhttp3.Headers

/**
 * Header redaction utility extracted from Chucker's OkHttpUtils.
 * Replaces header values matching [names] with "**".
 */
internal fun Headers.redact(names: Iterable<String>): Headers {
    val builder = newBuilder()
    for (name in names()) {
        if (names.any { userHeader -> userHeader.equals(name, ignoreCase = true) }) {
            builder[name] = "**"
        }
    }
    return builder.build()
}
