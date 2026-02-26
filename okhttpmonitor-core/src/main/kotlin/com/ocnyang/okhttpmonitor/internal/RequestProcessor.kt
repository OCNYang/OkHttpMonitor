package com.ocnyang.okhttpmonitor.internal

import com.ocnyang.okhttpmonitor.BodyDecoder
import com.ocnyang.okhttpmonitor.HttpTransaction
import com.ocnyang.okhttpmonitor.TransactionCollector
import okhttp3.Request
import okio.Buffer
import okio.ByteString
import okio.IOException

private const val BODY_CONTENT_TRUNCATED = "\n\n--- Content truncated ---"

internal class RequestProcessor(
    private val collector: TransactionCollector,
    private val maxContentLength: Long,
    private val headersToRedact: Set<String>,
    private val bodyDecoders: List<BodyDecoder>,
) {
    fun process(
        request: Request,
        transaction: HttpTransaction,
    ) {
        processMetadata(request, transaction)
        processPayload(request, transaction)
        collector.onRequestSent(transaction)
    }

    private fun processMetadata(
        request: Request,
        transaction: HttpTransaction,
    ) {
        transaction.apply {
            requestHeadersSize = request.headers.byteCount()
            requestHeaders = request.headers.redact(headersToRedact).toHeaderString()
            populateUrl(request.url)
            requestDate = System.currentTimeMillis()
            method = request.method
            requestContentType = request.body?.contentType()?.toString()
            requestPayloadSize = request.body?.contentLength()
        }
    }

    private fun processPayload(
        request: Request,
        transaction: HttpTransaction,
    ) {
        val body = request.body ?: return
        if (body.isOneShot()) {
            Logger.info("Skipping one shot request body")
            return
        }
        if (body.isDuplex()) {
            Logger.info("Skipping duplex request body")
            return
        }

        val requestSource =
            try {
                Buffer().apply { body.writeTo(this) }
            } catch (e: IOException) {
                Logger.error("Failed to read request payload", e)
                return
            }
        val limitingSource = LimitingSource(requestSource.uncompress(request.headers), maxContentLength)

        val contentBuffer = Buffer().apply { limitingSource.use { writeAll(it) } }

        val contentType = body.contentType()?.toString()
        val decodedContent = decodePayload(contentType, contentBuffer.readByteString())
        transaction.requestBody = decodedContent
        transaction.isRequestBodyEncoded = decodedContent == null
        if (decodedContent != null && limitingSource.isThresholdReached) {
            transaction.requestBody += BODY_CONTENT_TRUNCATED
        }
    }

    private fun decodePayload(
        contentType: String?,
        body: ByteString,
    ) = bodyDecoders
        .asSequence()
        .mapNotNull { decoder ->
            try {
                decoder.decodeRequest(contentType, body)
            } catch (e: IOException) {
                Logger.warn("Decoder $decoder failed to process request payload", e)
                null
            }
        }.firstOrNull()
}
