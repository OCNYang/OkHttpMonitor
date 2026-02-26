package com.ocnyang.okhttpmonitor

/**
 * Interface for collecting HTTP transaction data.
 * Users implement this interface to define their own reporting/analytics logic.
 *
 * **Threading:** Callbacks are invoked on the OkHttp calling thread.
 * - [onRequestSent]: Called synchronously before `chain.proceed()`
 * - [onResponseReceived]: Called when the application finishes consuming the response body
 *   (the thread depends on where the app reads the body), or synchronously if an IOException occurs
 *
 * If your reporting logic is time-consuming, switch to a background thread inside the callback.
 */
interface TransactionCollector {
    /**
     * Called when an HTTP request is sent. At this point, [transaction] contains
     * request metadata and body preview. Response fields are not yet populated.
     *
     * Default implementation is a no-op. Override only if you need request-phase reporting.
     */
    fun onRequestSent(transaction: HttpTransaction) {}

    /**
     * Called when an HTTP response is fully received, or when an error occurs.
     * At this point, [transaction] contains both request and response data.
     * If an IOException occurred, [HttpTransaction.error] is non-null.
     */
    fun onResponseReceived(transaction: HttpTransaction)
}
