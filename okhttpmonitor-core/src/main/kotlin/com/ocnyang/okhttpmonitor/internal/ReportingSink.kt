package com.ocnyang.okhttpmonitor.internal

import okio.Buffer
import okio.Sink
import okio.Timeout
import java.io.IOException

/**
 * A sink that reports the result of writing to it via [callback].
 *
 * Takes an input [downstream] sink and writes bytes from a source into it. Amount of bytes
 * to copy can be limited with [writeByteLimit]. Results are reported back to a client
 * when the sink is closed or when an exception occurs while writing bytes.
 *
 * Adapted from Chucker's ReportingSink: changed from File-based to generic Sink-based.
 * The write/flush/close/byte-counting/limiting logic is preserved as-is.
 */
internal class ReportingSink(
    private val downstream: Sink?,
    private val callback: Callback,
    private val writeByteLimit: Long = Long.MAX_VALUE,
) : Sink {
    private var totalByteCount = 0L
    private var isFailure = false
    private var isClosed = false

    override fun write(
        source: Buffer,
        byteCount: Long,
    ) {
        val previousTotalByteCount = totalByteCount
        totalByteCount += byteCount

        if (isFailure || previousTotalByteCount >= writeByteLimit) {
            // Still consume from source to prevent tempBuffer accumulation in TeeSource,
            // but discard the data since we're past the capture limit or in a failure state.
            source.skip(byteCount)
            return
        }

        val bytesToWrite =
            if (previousTotalByteCount + byteCount <= writeByteLimit) {
                byteCount
            } else {
                writeByteLimit - previousTotalByteCount
            }

        if (bytesToWrite == 0L) {
            source.skip(byteCount)
            return
        }

        try {
            downstream?.write(source, bytesToWrite)
            // If we wrote less than byteCount (due to limit), discard the rest
            val remaining = byteCount - bytesToWrite
            if (remaining > 0) {
                source.skip(remaining)
            }
        } catch (e: IOException) {
            callDownstreamFailure(e)
        }
    }

    override fun flush() {
        if (isFailure) return
        try {
            downstream?.flush()
        } catch (e: IOException) {
            callDownstreamFailure(e)
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        safeCloseDownstream()
        callback.onClosed(totalByteCount)
    }

    override fun timeout(): Timeout = downstream?.timeout() ?: Timeout.NONE

    private fun callDownstreamFailure(exception: IOException) {
        if (!isFailure) {
            isFailure = true
            safeCloseDownstream()
            callback.onFailure(exception)
        }
    }

    private fun safeCloseDownstream() =
        try {
            downstream?.close()
        } catch (e: IOException) {
            callDownstreamFailure(e)
        }

    interface Callback {
        /**
         * Called when the sink is closed. [sourceByteCount] is the exact amount of bytes
         * read from upstream, even if writing was limited by [writeByteLimit].
         */
        fun onClosed(sourceByteCount: Long)

        /**
         * Called when an [exception] was thrown while writing data.
         */
        fun onFailure(exception: IOException)
    }
}
