package com.ocnyang.ktormonitor.internal

import com.ocnyang.ktormonitor.BodyDecoder
import okio.ByteString
import java.nio.charset.Charset
import java.util.Locale
import kotlin.text.Charsets.UTF_8

// Built-in decoder for plain text bodies (text/*, application/json, application/xml, etc.)
object PlainTextDecoder : BodyDecoder {
    override fun decodeRequest(contentType: String?, body: ByteString): String? =
        body.tryDecodeAsPlainText(contentType)

    override fun decodeResponse(contentType: String?, body: ByteString): String? =
        body.tryDecodeAsPlainText(contentType)

    private fun ByteString.tryDecodeAsPlainText(contentType: String?): String? {
        if (!isProbablyPlainText) return null
        val charset = parseCharset(contentType) ?: UTF_8
        return string(charset)
    }
}

internal val ByteString.isProbablyPlainText: Boolean
    get() {
        val prefix = this.substring(0, minOf(16, size))
        for (i in 0 until prefix.size) {
            val b = prefix[i].toInt() and 0xFF
            if (b < 0x20 && b != '\t'.code && b != '\n'.code && b != '\r'.code) {
                return false
            }
        }
        return true
    }

internal fun parseCharset(contentType: String?): Charset? {
    if (contentType == null) return null
    val parts = contentType.split(";")
    for (part in parts) {
        val trimmed = part.trim().lowercase(Locale.ROOT)
        if (trimmed.startsWith("charset=")) {
            val charsetName = trimmed.removePrefix("charset=").trim().trim('"')
            return try {
                Charset.forName(charsetName)
            } catch (_: Exception) {
                null
            }
        }
    }
    return null
}
