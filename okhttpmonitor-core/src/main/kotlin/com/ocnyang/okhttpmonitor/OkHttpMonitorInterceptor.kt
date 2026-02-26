package com.ocnyang.okhttpmonitor

import com.ocnyang.okhttpmonitor.internal.PlainTextDecoder
import com.ocnyang.okhttpmonitor.internal.RequestProcessor
import com.ocnyang.okhttpmonitor.internal.ResponseProcessor
import com.ocnyang.okhttpmonitor.internal.addNonBlankPathSegments
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * An OkHttp Interceptor which captures HTTP request/response data and delivers it
 * to a [TransactionCollector] for analytics/monitoring/reporting.
 *
 * This is a pure Kotlin/JVM library with no Android dependencies.
 *
 * Usage:
 * ```kotlin
 * val monitor = OkHttpMonitorInterceptor.Builder()
 *     .collector(myCollector)
 *     .redactHeaders("Authorization", "Cookie")
 *     .maxContentLength(250_000L)
 *     .skipPaths("/health", "/ping")
 *     .build()
 *
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(monitor)
 *     .build()
 * ```
 */
class OkHttpMonitorInterceptor private constructor(
    builder: Builder,
) : Interceptor {

    private val collector = builder.collector
        ?: throw IllegalStateException("TransactionCollector must be set via .collector()")

    private val headersToRedact = builder.headersToRedact.toMutableSet()

    private val decoders: List<BodyDecoder> = builder.decoders + BUILT_IN_DECODERS

    private val requestProcessor = RequestProcessor(
        collector = collector,
        maxContentLength = builder.maxContentLength,
        headersToRedact = headersToRedact,
        bodyDecoders = decoders,
    )

    private val responseProcessor = ResponseProcessor(
        collector = collector,
        maxContentLength = builder.maxContentLength,
        headersToRedact = headersToRedact,
        bodyDecoders = decoders,
    )

    private val skipPaths = builder.skipPaths.toSet()
    private val skipPathsRegex = builder.skipPathsRegex.toSet()
    private val skipDomains = builder.skipDomains.toSet()
    private val skipDomainRegex = builder.skipDomainRegex.toSet()

    /** Adds [headerName] into the set of headers to redact. */
    fun redactHeader(vararg headerName: String) {
        headersToRedact.addAll(headerName)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val transaction = HttpTransaction()
        val request = chain.request()
        val path = request.url.encodedPath
        val host = request.url.host

        val shouldSkipPath = skipPaths.contains(path) || skipPathsRegex.any { it.matches(path) }
        val shouldSkipDomain =
            shouldSkipPath || skipDomains.contains(host) || skipDomainRegex.any { it.matches(host) }
        val shouldProcess = !(shouldSkipPath || shouldSkipDomain)

        if (shouldProcess) {
            requestProcessor.process(request, transaction)
        }

        val response =
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (shouldProcess) {
                    transaction.error = e.toString()
                    collector.onResponseReceived(transaction)
                }
                throw e
            }

        return if (shouldProcess) {
            responseProcessor.process(response, transaction)
        } else {
            response
        }
    }

    /**
     * Assembles a new [OkHttpMonitorInterceptor].
     */
    class Builder {
        internal var collector: TransactionCollector? = null
        internal var maxContentLength = MAX_CONTENT_LENGTH
        internal var headersToRedact = emptySet<String>()
        internal var decoders = emptyList<BodyDecoder>()
        internal val skipPaths = mutableSetOf<String>()
        internal val skipPathsRegex = mutableSetOf<Regex>()
        internal val skipDomains = mutableSetOf<String>()
        internal val skipDomainRegex = mutableSetOf<Regex>()

        /**
         * Sets the [TransactionCollector] that receives captured HTTP transaction data.
         * This is required.
         */
        fun collector(collector: TransactionCollector): Builder =
            apply { this.collector = collector }

        /**
         * Sets the maximum length for request and response content before truncation.
         * Default is 250,000 bytes.
         */
        fun maxContentLength(length: Long): Builder =
            apply { this.maxContentLength = length }

        /**
         * Sets headers that will be redacted (replaced with "**") by name.
         */
        fun redactHeaders(headerNames: Iterable<String>): Builder =
            apply { this.headersToRedact = headerNames.toSet() }

        /**
         * Sets headers that will be redacted (replaced with "**") by name.
         */
        fun redactHeaders(vararg headerNames: String): Builder =
            apply { this.headersToRedact = headerNames.toSet() }

        /**
         * Adds a [decoder] into the processing pipeline.
         * Decoders are applied in order; the first non-null result is used.
         */
        fun addBodyDecoder(decoder: BodyDecoder): Builder =
            apply { this.decoders += decoder }

        /**
         * Sets exact paths to skip. When a request path matches, it will not be captured.
         */
        fun skipPaths(vararg paths: String): Builder =
            apply {
                paths.forEach { candidatePath ->
                    val httpUrl = HttpUrl.Builder()
                        .scheme("https")
                        .host("example.com")
                        .addNonBlankPathSegments(candidatePath)
                        .build()
                    this@Builder.skipPaths.add(httpUrl.encodedPath)
                }
            }

        /**
         * Sets regex patterns to match paths to skip.
         */
        fun skipPaths(paths: Regex): Builder =
            apply { this.skipPathsRegex.add(paths) }

        /**
         * Sets exact domain names to skip (case-insensitive).
         */
        fun skipDomains(vararg domains: String): Builder =
            apply { this@Builder.skipDomains.addAll(domains.map { it.lowercase() }) }

        /**
         * Sets regex patterns to match domains to skip.
         */
        fun skipDomains(domains: Regex): Builder =
            apply { this.skipDomainRegex.add(domains) }

        /**
         * Creates a new [OkHttpMonitorInterceptor] instance.
         */
        fun build(): OkHttpMonitorInterceptor = OkHttpMonitorInterceptor(this)
    }

    private companion object {
        private const val MAX_CONTENT_LENGTH = 250_000L

        private val BUILT_IN_DECODERS: List<BodyDecoder> = listOf(PlainTextDecoder)
    }
}
