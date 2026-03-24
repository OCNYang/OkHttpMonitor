package com.ocnyang.ktormonitor.internal

import com.ocnyang.ktormonitor.BodyDecoder
import com.ocnyang.ktormonitor.HttpTransaction
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.http.HttpStatusCode
import okio.ByteString.Companion.toByteString

internal class ResponseCapture(
    private val maxContentLength: Long,
    private val headersToRedact: Set<String>,
    private val bodyDecoders: List<BodyDecoder>,
) {
    /**
     * Captures response metadata and body into [transaction], and returns a [HttpClientCall]
     * whose body can still be fully consumed by the caller (via [HttpClientCall.save]).
     */
    suspend fun capture(
        call: HttpClientCall,
        transaction: HttpTransaction,
        requestStartTime: Long,
    ): HttpClientCall {
        // Buffer the response body so both we and the caller can read it
        val savedCall = call.save()
        val response = savedCall.response

        // Metadata
        transaction.responseDate = System.currentTimeMillis()
        transaction.tookMs = transaction.responseDate!! - requestStartTime
        transaction.responseCode = response.status.value
        transaction.responseMessage = response.status.description
        transaction.protocol = response.version.toString()

        val responseHeaders = response.headers
        transaction.responseHeaders = responseHeaders.redact(headersToRedact).toHeaderString()
        transaction.responseHeadersSize = responseHeaders.byteCount()
        transaction.responseContentType = responseHeaders["Content-Type"]

        // Body
        captureBody(savedCall, transaction, responseHeaders)

        return savedCall
    }

    private suspend fun captureBody(
        savedCall: HttpClientCall,
        transaction: HttpTransaction,
        headers: io.ktor.http.Headers,
    ) {
        val status = transaction.responseCode ?: return
        // Skip body for 1xx, 204, 304
        if (status in 100..199 || status == HttpStatusCode.NoContent.value || status == HttpStatusCode.NotModified.value) {
            return
        }

        val rawBytes: ByteArray = try {
            savedCall.body()
        } catch (e: Exception) {
            Logger.error("Failed to read response body", e)
            return
        }

        transaction.responsePayloadSize = rawBytes.size.toLong()

        val limited: ByteArray = if (rawBytes.size > maxContentLength) {
            rawBytes.copyOf(maxContentLength.toInt())
        } else {
            rawBytes
        }
        val truncated = rawBytes.size > maxContentLength

        val uncompressed: ByteArray = limited.uncompress(headers)
        val byteString = uncompressed.toByteString()
        val contentType = transaction.responseContentType

        val decoded = bodyDecoders.asSequence()
            .mapNotNull { decoder ->
                try { decoder.decodeResponse(contentType, byteString) } catch (_: Exception) { null }
            }.firstOrNull()

        transaction.responseBody = if (decoded != null && truncated) decoded + "\n\n--- Content truncated ---" else decoded
        transaction.isResponseBodyEncoded = decoded == null
    }
}
