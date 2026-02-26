package com.ocnyang.okhttpmonitor.internal

import okio.Buffer
import okio.ForwardingSource
import okio.Source

/**
 * A source that limits reading to [bytesCountThreshold] bytes.
 * Once the threshold is reached, subsequent reads return -1 (EOF).
 *
 * Reused from Chucker (package name changed only).
 */
internal class LimitingSource(
    delegate: Source,
    private val bytesCountThreshold: Long,
) : ForwardingSource(delegate) {
    private var bytesRead = 0L
    val isThresholdReached get() = bytesRead >= bytesCountThreshold

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ) = if (!isThresholdReached) {
        super.read(sink, byteCount).also { bytesRead += it }
    } else {
        -1L
    }
}
