package com.ocnyang.okhttpmonitor

import okio.ByteString
import okio.IOException

/**
 * Decodes HTTP request and response bodies to human-readable text.
 *
 * Unlike Chucker's BodyDecoder which takes OkHttp Request/Response objects,
 * this interface only requires the content type and raw bytes, making it
 * framework-agnostic.
 */
interface BodyDecoder {
    /**
     * Returns a text representation of a request [body], or `null` if this decoder
     * cannot handle the given [contentType].
     *
     * [body] is guaranteed to be uncompressed and no longer than maxContentLength.
     */
    @Throws(IOException::class)
    fun decodeRequest(contentType: String?, body: ByteString): String?

    /**
     * Returns a text representation of a response [body], or `null` if this decoder
     * cannot handle the given [contentType].
     *
     * [body] is guaranteed to be uncompressed and no longer than maxContentLength.
     */
    @Throws(IOException::class)
    fun decodeResponse(contentType: String?, body: ByteString): String?
}
