package com.ocnyang.okhttpmonitor.internal

import okio.Buffer
import okio.ByteString
import java.io.EOFException
import kotlin.math.min

private const val MAX_PREFIX_SIZE = 64L
private const val CODE_POINT_SIZE = 16

/**
 * Returns true if the [Buffer] contains human readable text. Uses a small sample
 * of code points to detect unicode control characters commonly used in binary file signatures.
 *
 * Reused from Chucker (package name changed only).
 */
internal val Buffer.isProbablyPlainText
    get() =
        try {
            val prefix = Buffer()
            val byteCount = min(size, MAX_PREFIX_SIZE)
            copyTo(prefix, 0, byteCount)
            sequence { while (!prefix.exhausted()) yield(prefix.readUtf8CodePoint()) }
                .take(CODE_POINT_SIZE)
                .all { codePoint -> codePoint.isPlainTextChar() }
        } catch (_: EOFException) {
            false
        }

internal val ByteString.isProbablyPlainText: Boolean
    get() {
        val byteCount = min(size, MAX_PREFIX_SIZE.toInt())
        return Buffer().write(this, offset = 0, byteCount).isProbablyPlainText
    }

private fun Int.isPlainTextChar() = Character.isWhitespace(this) || !Character.isISOControl(this)
