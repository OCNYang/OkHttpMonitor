package com.ocnyang.ktormonitor.internal

import io.ktor.http.Headers
import okio.Buffer
import okio.Source
import okio.buffer
import okio.gzip
import okio.source
import org.brotli.dec.BrotliInputStream
import java.util.Locale

private val supportedEncodings = listOf("identity", "gzip", "br")

internal val Headers.hasSupportedContentEncoding: Boolean
    get() =
        get("Content-Encoding")
            ?.takeIf { it.isNotEmpty() }
            ?.let { it.lowercase(Locale.ROOT) in supportedEncodings }
            ?: true

private val Headers.containsGzip: Boolean
    get() = get("Content-Encoding").equals("gzip", ignoreCase = true)

private val Headers.containsBrotli: Boolean
    get() = get("Content-Encoding").equals("br", ignoreCase = true)

internal fun Source.uncompress(headers: Headers): Source =
    when {
        headers.containsGzip -> gzip()
        headers.containsBrotli -> BrotliInputStream(this.buffer().inputStream()).source()
        else -> this
    }

/**
 * Decompresses [bytes] according to Content-Encoding in [headers] and returns the raw bytes.
 * Returns the input unchanged if no compression is applied.
 */
internal fun ByteArray.uncompress(headers: Headers): ByteArray {
    if (isEmpty()) return this
    val source = Buffer().apply { write(this@uncompress) }
    val uncompressed = source.uncompress(headers)
    return Buffer().apply { writeAll(uncompressed) }.readByteArray()
}
