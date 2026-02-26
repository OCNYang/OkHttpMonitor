package com.ocnyang.okhttpmonitor.internal

import okhttp3.HttpUrl

private const val PATH_SEGMENTS_DELIMITER = "/"

/**
 * Adds non-blank path segments from a candidate path string.
 * Reused from Chucker (package name changed only).
 */
internal fun HttpUrl.Builder.addNonBlankPathSegments(candidatePath: String): HttpUrl.Builder =
    apply {
        candidatePath
            .split(PATH_SEGMENTS_DELIMITER)
            .filter { it.isNotBlank() }
            .forEach { item -> addPathSegment(item) }
    }
