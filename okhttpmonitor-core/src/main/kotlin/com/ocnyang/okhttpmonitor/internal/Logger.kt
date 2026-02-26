package com.ocnyang.okhttpmonitor.internal

/**
 * Simple logging interface for internal library use.
 * Delegates to java.util.logging by default. Users can replace it.
 */
internal object Logger {
    private val javaLogger = java.util.logging.Logger.getLogger("OkHttpMonitor")

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
