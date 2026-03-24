package com.ocnyang.ktormonitor.internal

import com.ocnyang.ktormonitor.BodyDecoder
import com.ocnyang.ktormonitor.HttpTransaction
import com.ocnyang.ktormonitor.TransactionCollector
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.util.toByteArray
import okio.ByteString.Companion.toByteString

private const val BODY_CONTENT_TRUNCATED = "\n\n--- Content truncated ---"

internal class RequestCapture(
    private val collector: TransactionCollector,
    private val maxContentLength: Long,
    private val headersToRedact: Set<String>,
    private val bodyDecoders: List<BodyDecoder>,
) {
    suspend fun capture(request: HttpRequestBuilder, transaction: HttpTransaction) {
        val headers = request.headers.build()

        transaction.requestDate = System.currentTimeMillis()
        transaction.method = request.method.value
        transaction.populateUrl(request.url.build())
        transaction.requestHeaders = headers.redact(headersToRedact).toHeaderString()
        transaction.requestHeadersSize = headers.byteCount()

        val body = request.body
        if (body is OutgoingContent) {
            transaction.requestContentType = body.contentType?.toString()
            transaction.requestPayloadSize = body.contentLength

            captureBody(body, headers.redact(headersToRedact), transaction)
        }

        collector.onRequestSent(transaction)
    }

    private suspend fun captureBody(
        body: OutgoingContent,
        headers: io.ktor.http.Headers,
        transaction: HttpTransaction,
    ) {
        val rawBytes = try {
            when (body) {
                is OutgoingContent.ByteArrayContent -> body.bytes()
                is OutgoingContent.ReadChannelContent -> body.readFrom().toByteArray()
                is OutgoingContent.WriteChannelContent -> null // streaming — skip
                else -> null
            }
        } catch (e: Exception) {
            Logger.warn("Failed to read request body", e)
            return
        } ?: return

        val limited: ByteArray = if (rawBytes.size > maxContentLength) {
            rawBytes.copyOf(maxContentLength.toInt())
        } else {
            rawBytes
        }
        val truncated = rawBytes.size > maxContentLength

        val uncompressed: ByteArray = limited.uncompress(headers)
        val byteString = uncompressed.toByteString()
        val contentType = transaction.requestContentType

        val decoded = bodyDecoders.asSequence()
            .mapNotNull { decoder ->
                try { decoder.decodeRequest(contentType, byteString) } catch (_: Exception) { null }
            }.firstOrNull()

        transaction.requestBody = if (decoded != null && truncated) decoded + BODY_CONTENT_TRUNCATED else decoded
        transaction.isRequestBodyEncoded = decoded == null
    }
}
