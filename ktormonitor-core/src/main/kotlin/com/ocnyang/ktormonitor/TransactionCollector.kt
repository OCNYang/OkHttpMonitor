package com.ocnyang.ktormonitor

/**
 * Interface for collecting HTTP transaction data.
 * Users implement this interface to define their own reporting/analytics logic.
 *
 * **Threading:** Callbacks are invoked on the Ktor engine thread (coroutine context).
 * If your reporting logic is time-consuming, switch to a background dispatcher inside the callback.
 */
interface TransactionCollector {
    /**
     * Called when an HTTP request is about to be sent. At this point, [transaction] contains
     * request metadata and body preview. Response fields are not yet populated.
     *
     * Default implementation is a no-op. Override only if you need request-phase reporting.
     */
    fun onRequestSent(transaction: HttpTransaction) {}

    /**
     * Called when an HTTP response is fully received, or when an error occurs.
     * At this point, [transaction] contains both request and response data.
     * If an exception occurred, [HttpTransaction.error] is non-null.
     */
    fun onResponseReceived(transaction: HttpTransaction)
}
