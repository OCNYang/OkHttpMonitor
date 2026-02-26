package com.ocnyang.okhttpmonitor.internal

import com.ocnyang.okhttpmonitor.BodyDecoder
import com.ocnyang.okhttpmonitor.HttpTransaction
import com.ocnyang.okhttpmonitor.TransactionCollector
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ByteString
import okio.IOException
import okio.buffer

internal class ResponseProcessor(
    private val collector: TransactionCollector,
    private val maxContentLength: Long,
    private val headersToRedact: Set<String>,
    private val bodyDecoders: List<BodyDecoder>,
) {
    fun process(
        response: Response,
        transaction: HttpTransaction,
    ): Response {
        processResponseMetadata(response, transaction)
        return multiCastResponse(response, transaction)
    }

    private fun processResponseMetadata(
        response: Response,
        transaction: HttpTransaction,
    ) {
        transaction.apply {
            // Includes headers added later in the chain
            requestHeadersSize = response.request.headers.byteCount()
            requestHeaders = response.request.headers.redact(headersToRedact).toHeaderString()
            responseHeadersSize = response.headers.byteCount()
            responseHeaders = response.headers.redact(headersToRedact).toHeaderString()

            if (response.sentRequestAtMillis > 0) {
                requestDate = response.sentRequestAtMillis
            }

            responseDate =
                if (response.receivedResponseAtMillis > 0) {
                    response.receivedResponseAtMillis
                } else {
                    System.currentTimeMillis()
                }

            protocol = response.protocol.toString()
            responseCode = response.code
            responseMessage = response.message

            response.handshake?.let { handshake ->
                responseTlsVersion = handshake.tlsVersion.javaName
                responseCipherSuite = handshake.cipherSuite.javaName
            }

            responseContentType = response.contentType

            tookMs =
                if (response.sentRequestAtMillis > 0 && response.receivedResponseAtMillis > 0) {
                    response.receivedResponseAtMillis - response.sentRequestAtMillis
                } else if (requestDate != null && responseDate != null) {
                    responseDate!! - requestDate!!
                } else {
                    null
                }
        }
    }

    private fun multiCastResponse(
        response: Response,
        transaction: HttpTransaction,
    ): Response {
        val responseBody = response.body
        if (!response.hasBody() || responseBody == null) {
            collector.onResponseReceived(transaction)
            return response
        }

        val contentType = responseBody.contentType()
        val contentLength = responseBody.contentLength()

        val captureBuffer = Buffer()
        val sideStream = ReportingSink(
            downstream = captureBuffer,
            callback = ResponseReportingSinkCallback(response, transaction, captureBuffer),
            writeByteLimit = maxContentLength,
        )
        val upstream = TeeSource(responseBody.source(), sideStream)

        return response
            .newBuilder()
            .body(upstream.buffer().asResponseBody(contentType, contentLength))
            .build()
    }

    private fun processPayload(
        response: Response,
        payload: Buffer,
        transaction: HttpTransaction,
    ) {
        if (payload.size == 0L) return

        val contentType = response.body?.contentType()?.toString()
        val decodedContent = decodePayload(contentType, payload.readByteString())
        transaction.responseBody = decodedContent
        transaction.isResponseBodyEncoded = decodedContent == null
    }

    private fun decodePayload(
        contentType: String?,
        body: ByteString,
    ) = bodyDecoders
        .asSequence()
        .mapNotNull { decoder ->
            try {
                decoder.decodeResponse(contentType, body)
            } catch (e: IOException) {
                Logger.warn("Decoder $decoder failed to process response payload", e)
                null
            }
        }.firstOrNull()

    private inner class ResponseReportingSinkCallback(
        private val response: Response,
        private val transaction: HttpTransaction,
        private val captureBuffer: Buffer,
    ) : ReportingSink.Callback {
        override fun onClosed(sourceByteCount: Long) {
            readResponsePayload()?.let { payload ->
                processPayload(response, payload, transaction)
            }
            transaction.responsePayloadSize = sourceByteCount
            collector.onResponseReceived(transaction)
        }

        override fun onFailure(exception: IOException) {
            Logger.error("Failed to read response payload", exception)
        }

        private fun readResponsePayload(): Buffer? =
            try {
                if (captureBuffer.size == 0L) {
                    null
                } else {
                    val uncompressed = captureBuffer.uncompress(response.headers)
                    Buffer().apply { writeAll(uncompressed) }
                }
            } catch (e: IOException) {
                Logger.error("Response payload couldn't be processed", e)
                null
            }
    }
}
