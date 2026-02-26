package com.ocnyang.okhttpmonitor

import okhttp3.HttpUrl

/**
 * Represents a full HTTP transaction (request + response).
 * This is a pure data class with no Android or Room dependencies.
 */
class HttpTransaction {
    // Request fields
    var requestDate: Long? = null
    var method: String? = null
    var url: String? = null
    var host: String? = null
    var path: String? = null
    var scheme: String? = null
    var requestContentType: String? = null
    var requestPayloadSize: Long? = null
    var requestHeadersSize: Long? = null
    var requestHeaders: String? = null
    var requestBody: String? = null
    var isRequestBodyEncoded: Boolean = false

    // Response fields
    var responseDate: Long? = null
    var protocol: String? = null
    var responseCode: Int? = null
    var responseMessage: String? = null
    var responseTlsVersion: String? = null
    var responseCipherSuite: String? = null
    var responseContentType: String? = null
    var responsePayloadSize: Long? = null
    var responseHeadersSize: Long? = null
    var responseHeaders: String? = null
    var responseBody: String? = null
    var isResponseBodyEncoded: Boolean = false

    // Timing
    var tookMs: Long? = null

    // Error
    var error: String? = null

    val status: Status
        get() = when {
            error != null -> Status.Failed
            responseCode == null -> Status.Requested
            else -> Status.Complete
        }

    val isError: Boolean
        get() = error != null || (responseCode != null && responseCode !in 200..299)

    enum class Status {
        Requested,
        Complete,
        Failed,
    }

    fun populateUrl(httpUrl: HttpUrl) {
        url = httpUrl.toString()
        host = httpUrl.host
        path = httpUrl.encodedPath
        scheme = httpUrl.scheme
    }

    /**
     * Converts this transaction to a Map<String, String> suitable for analytics reporting.
     * Fields exceeding [maxFieldLength] will be truncated.
     * Null fields are omitted.
     */
    fun toMap(maxFieldLength: Int = Int.MAX_VALUE): Map<String, String> {
        val map = mutableMapOf<String, String>()

        fun put(key: String, value: String?) {
            if (value != null) {
                map[key] = if (value.length > maxFieldLength) value.take(maxFieldLength) else value
            }
        }

        fun putLong(key: String, value: Long?) {
            if (value != null) map[key] = value.toString()
        }

        fun putInt(key: String, value: Int?) {
            if (value != null) map[key] = value.toString()
        }

        fun putBool(key: String, value: Boolean) {
            map[key] = value.toString()
        }

        // Request
        putLong("request_date", requestDate)
        put("method", method)
        put("url", url)
        put("host", host)
        put("path", path)
        put("scheme", scheme)
        put("request_content_type", requestContentType)
        putLong("request_payload_size", requestPayloadSize)
        putLong("request_headers_size", requestHeadersSize)
        put("request_headers", requestHeaders)
        put("request_body", requestBody)
        putBool("is_request_body_encoded", isRequestBodyEncoded)

        // Response
        putLong("response_date", responseDate)
        put("protocol", protocol)
        putInt("response_code", responseCode)
        put("response_message", responseMessage)
        put("response_content_type", responseContentType)
        putLong("response_payload_size", responsePayloadSize)
        putLong("response_headers_size", responseHeadersSize)
        put("response_headers", responseHeaders)
        put("response_body", responseBody)
        putBool("is_response_body_encoded", isResponseBodyEncoded)
        put("tls_version", responseTlsVersion)
        put("cipher_suite", responseCipherSuite)

        // Timing & Status
        putLong("took_ms", tookMs)
        put("error", error)
        put("status", status.name)

        return map
    }
}
