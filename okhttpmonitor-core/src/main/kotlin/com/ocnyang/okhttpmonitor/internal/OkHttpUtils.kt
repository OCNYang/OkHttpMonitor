package com.ocnyang.okhttpmonitor.internal

import okhttp3.Headers
import okhttp3.Response
import okio.Source
import okio.buffer
import okio.gzip
import okio.source
import org.brotli.dec.BrotliInputStream
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_NO_CONTENT
import java.net.HttpURLConnection.HTTP_OK
import java.util.Locale

private const val HTTP_CONTINUE = 100

/**
 * OkHttp utility extensions adapted from Chucker.
 * Extracted header redaction and response body detection logic.
 */

/** Returns true if the response must have a (possibly 0-length) body. See RFC 7231. */
internal fun Response.hasBody(): Boolean {
    if (request.method == "HEAD") {
        return false
    }

    val responseCode = code
    if ((responseCode < HTTP_CONTINUE || responseCode >= HTTP_OK) &&
        (responseCode != HTTP_NO_CONTENT) &&
        (responseCode != HTTP_NOT_MODIFIED)
    ) {
        return true
    }

    return ((contentLength > 0) || isChunked)
}

private val Response.contentLength: Long
    get() = this.header("Content-Length")?.toLongOrNull() ?: -1L

internal val Response.isChunked: Boolean
    get() = this.header("Transfer-Encoding").equals("chunked", ignoreCase = true)

internal val Response.contentType: String?
    get() = this.header("Content-Type")

private val Headers.containsGzip: Boolean
    get() = this["Content-Encoding"].equals("gzip", ignoreCase = true)

private val Headers.containsBrotli: Boolean
    get() = this["Content-Encoding"].equals("br", ignoreCase = true)

private val supportedEncodings = listOf("identity", "gzip", "br")

internal val Headers.hasSupportedContentEncoding: Boolean
    get() =
        get("Content-Encoding")
            ?.takeIf { it.isNotEmpty() }
            ?.let { it.lowercase(Locale.ROOT) in supportedEncodings }
            ?: true

internal fun Source.uncompress(headers: Headers) =
    when {
        headers.containsGzip -> gzip()
        headers.containsBrotli -> BrotliInputStream(this.buffer().inputStream()).source()
        else -> this
    }
