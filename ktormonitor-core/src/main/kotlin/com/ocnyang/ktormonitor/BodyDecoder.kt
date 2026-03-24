package com.ocnyang.ktormonitor

import okio.ByteString
import okio.IOException

/**
 * Decodes HTTP request and response bodies to human-readable text.
 *
 * This interface only requires the content type and raw bytes, making it framework-agnostic.
 * The same decoder implementations work with both OkHttp and Ktor monitor modules.
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
