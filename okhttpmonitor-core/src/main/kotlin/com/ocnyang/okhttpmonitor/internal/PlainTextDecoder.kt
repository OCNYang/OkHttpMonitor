package com.ocnyang.okhttpmonitor.internal

import com.ocnyang.okhttpmonitor.BodyDecoder
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.ByteString
import kotlin.text.Charsets.UTF_8

/**
 * Built-in decoder that handles plain text bodies.
 * Adapted from Chucker's PlainTextDecoder, using the new BodyDecoder interface.
 */
internal object PlainTextDecoder : BodyDecoder {
    override fun decodeRequest(contentType: String?, body: ByteString): String? =
        body.tryDecodeAsPlainText(null, contentType?.toMediaTypeOrNull())

    override fun decodeResponse(contentType: String?, body: ByteString): String? =
        body.tryDecodeAsPlainText(null, contentType?.toMediaTypeOrNull())

    /**
     * Internal variant used by processors that have access to headers for encoding detection.
     */
    internal fun decodeWithHeaders(headers: Headers, contentType: MediaType?, body: ByteString): String? =
        body.tryDecodeAsPlainText(headers, contentType)

    private fun ByteString.tryDecodeAsPlainText(
        headers: Headers?,
        contentType: MediaType?,
    ): String? {
        val hasSupported = headers?.hasSupportedContentEncoding ?: true
        return if (hasSupported && isProbablyPlainText) {
            string(contentType?.charset() ?: UTF_8)
        } else {
            null
        }
    }
}
