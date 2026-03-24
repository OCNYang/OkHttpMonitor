package com.ocnyang.ktormonitor

import com.ocnyang.ktormonitor.internal.PlainTextDecoder
import com.ocnyang.ktormonitor.internal.RequestCapture
import com.ocnyang.ktormonitor.internal.ResponseCapture
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url

/**
 * Ktor client plugin that captures HTTP request/response data and delivers it
 * to a [TransactionCollector] for analytics/monitoring/reporting.
 *
 * Usage:
 * ```kotlin
 * val client = HttpClient {
 *     install(KtorMonitorPlugin) {
 *         collector = myCollector
 *         redactHeaders("Authorization", "Cookie")
 *         maxContentLength = 250_000L
 *         skipPaths("/health", "/ping")
 *     }
 * }
 * ```
 */
class KtorMonitorConfig {
    /** Required: receives captured HTTP transaction data. */
    var collector: TransactionCollector? = null

    /** Maximum body bytes to capture before truncation. Default: 250 KB. */
    var maxContentLength: Long = 250_000L

    /** Header names whose values will be replaced with "**". */
    val headersToRedact: MutableSet<String> = mutableSetOf()

    /** Additional body decoders. Built-in [PlainTextDecoder] is always appended last. */
    val extraDecoders: MutableList<BodyDecoder> = mutableListOf()

    /** Exact path strings to skip (e.g. "/health"). */
    val skipPaths: MutableSet<String> = mutableSetOf()

    /** Regex patterns for paths to skip. */
    val skipPathsRegex: MutableSet<Regex> = mutableSetOf()

    /** Exact host names to skip (case-insensitive, e.g. "analytics.com"). */
    val skipDomains: MutableSet<String> = mutableSetOf()

    /** Regex patterns for hosts to skip. */
    val skipDomainsRegex: MutableSet<Regex> = mutableSetOf()

    fun redactHeaders(vararg names: String) {
        headersToRedact.addAll(names)
    }

    fun skipPaths(vararg paths: String) {
        skipPaths.addAll(paths)
    }

    fun skipDomains(vararg domains: String) {
        skipDomains.addAll(domains.map { it.lowercase() })
    }

    fun addBodyDecoder(decoder: BodyDecoder) {
        extraDecoders.add(decoder)
    }
}

val KtorMonitorPlugin: ClientPlugin<KtorMonitorConfig> =
    createClientPlugin("KtorMonitor", ::KtorMonitorConfig) {
        val collector = pluginConfig.collector ?: return@createClientPlugin
        val decoders: List<BodyDecoder> = pluginConfig.extraDecoders + PlainTextDecoder

        val requestCapture = RequestCapture(
            collector = collector,
            maxContentLength = pluginConfig.maxContentLength,
            headersToRedact = pluginConfig.headersToRedact,
            bodyDecoders = decoders,
        )
        val responseCapture = ResponseCapture(
            maxContentLength = pluginConfig.maxContentLength,
            headersToRedact = pluginConfig.headersToRedact,
            bodyDecoders = decoders,
        )

        client.plugin(HttpSend).intercept { request ->
            val transaction = HttpTransaction()
            val requestUrl = request.url.build()

            val shouldSkip = shouldSkip(requestUrl, pluginConfig)
            if (shouldSkip) {
                return@intercept execute(request)
            }

            // Capture request
            requestCapture.capture(request, transaction)

            val startTime = System.currentTimeMillis()

            // Execute request
            val call = try {
                execute(request)
            } catch (e: Exception) {
                transaction.error = e.toString()
                transaction.tookMs = System.currentTimeMillis() - startTime
                collector.onResponseReceived(transaction)
                throw e
            }

            // Capture response — returns savedCall so caller can still read the body
            val savedCall = responseCapture.capture(call, transaction, startTime)
            collector.onResponseReceived(transaction)

            savedCall
        }
    }

private fun shouldSkip(url: Url, config: KtorMonitorConfig): Boolean {
    val path = url.encodedPath
    val host = url.host.lowercase()
    return config.skipPaths.contains(path) ||
        config.skipPathsRegex.any { it.matches(path) } ||
        config.skipDomains.contains(host) ||
        config.skipDomainsRegex.any { it.matches(host) }
}
