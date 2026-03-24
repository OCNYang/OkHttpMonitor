package com.ocnyang.ktormonitor.internal

/**
 * Simple logging interface for internal library use.
 */
internal object Logger {
    private val javaLogger = java.util.logging.Logger.getLogger("KtorMonitor")

    fun info(message: String, throwable: Throwable? = null) {
        javaLogger.log(java.util.logging.Level.INFO, message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        javaLogger.log(java.util.logging.Level.WARNING, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        javaLogger.log(java.util.logging.Level.SEVERE, message, throwable)
    }
}
