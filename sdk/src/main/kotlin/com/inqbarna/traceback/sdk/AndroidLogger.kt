/*
 * MIT License
 *
 * Copyright (c) 2025 inqbarna
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.inqbarna.traceback.sdk

import android.util.Log

/**
 * Minimal logger interface that follows the commonly used SLF4J surface so the rest of the code
 * can keep calling logger.debug/info/warn/error(...) without depending on SLF4J.
 */

/** Log levels used by the internal logger. We map TRACE to VERBOSE to match android.util.Log. */
@Suppress("unused")
internal enum class LogLevel(val priority: Int) {
    TRACE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR)
}

internal interface Logger {
    var minLevel: LogLevel

    fun isDebugEnabled(): Boolean
    fun isInfoEnabled(): Boolean
    fun isWarnEnabled(): Boolean
    fun isErrorEnabled(): Boolean

    fun debug(msg: String)
    fun debug(msg: String, t: Throwable)
    fun debug(format: String, vararg args: Any?)

    fun info(msg: String)
    fun info(msg: String, t: Throwable)
    fun info(format: String, vararg args: Any?)

    fun warn(msg: String)
    fun warn(msg: String, t: Throwable)
    fun warn(format: String, vararg args: Any?)

    fun error(msg: String)
    fun error(msg: String, t: Throwable)
    fun error(format: String, vararg args: Any?)
}

private const val TRACEBACK_TAG = "Traceback"

private fun formatMessage(format: String, args: Array<out Any?>): String {
    if (args.isEmpty()) return format
    val result = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c == '{' && i + 1 < format.length && format[i + 1] == '}') {
            val replacement = if (argIndex < args.size) args[argIndex++]?.toString() ?: "null" else "{}"
            result.append(replacement)
            i += 2
            continue
        }
        result.append(c)
        i++
    }
    // append any remaining args separated by space
    while (argIndex < args.size) {
        result.append(' ').append(args[argIndex++]?.toString() ?: "null")
    }
    return result.toString()
}

private class AndroidLogger(private val name: String? = null, override var minLevel: LogLevel = LogLevel.DEBUG) : Logger {
    private val tag: String = TRACEBACK_TAG

    private fun prefix(msg: String): String = if (name != null) "[$name] $msg" else msg

    private fun shouldLog(priority: Int): Boolean = priority >= minLevel.priority && Log.isLoggable(tag, priority)

    override fun isDebugEnabled(): Boolean = shouldLog(Log.DEBUG)
    override fun isInfoEnabled(): Boolean = shouldLog(Log.INFO)
    override fun isWarnEnabled(): Boolean = shouldLog(Log.WARN)
    override fun isErrorEnabled(): Boolean = shouldLog(Log.ERROR)

    // Central logging helper: check levels, extract throwable (first or last arg), format and dispatch.
    private fun log(priority: Int, formatOrMsg: String, vararg args: Any?) {
        // Enforce configured minimum level first
        if (priority < minLevel.priority) return
        // Respect Android's tag-level filter
        if (!Log.isLoggable(tag, priority)) return

        // Extract throwable if provided as first arg (explicit) or last arg (SLF4J-style)
        val throwable: Throwable?
        val usedArgs: Array<out Any?>
        if (args.isNotEmpty() && args[0] is Throwable) {
            throwable = args[0] as Throwable
            usedArgs = if (args.size > 1) args.copyOfRange(1, args.size) else emptyArray()
        } else if (args.isNotEmpty() && args[args.size - 1] is Throwable) {
            throwable = args[args.size - 1] as Throwable
            usedArgs = if (args.size > 1) args.copyOfRange(0, args.size - 1) else emptyArray()
        } else {
            throwable = null
            usedArgs = args
        }

        val message = prefix(formatMessage(formatOrMsg, usedArgs))

        when (priority) {
            Log.VERBOSE -> if (throwable != null) Log.v(tag, message, throwable) else Log.v(tag, message)
            Log.DEBUG -> if (throwable != null) Log.d(tag, message, throwable) else Log.d(tag, message)
            Log.INFO -> if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            Log.WARN -> if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            Log.ERROR -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            else -> Log.println(priority, tag, message)
        }
    }

    override fun debug(msg: String) { log(Log.DEBUG, msg) }
    override fun debug(msg: String, t: Throwable) { log(Log.DEBUG, msg, t) }
    override fun debug(format: String, vararg args: Any?) { log(Log.DEBUG, format, *args) }

    override fun info(msg: String) { log(Log.INFO, msg) }
    override fun info(msg: String, t: Throwable) { log(Log.INFO, msg, t) }
    override fun info(format: String, vararg args: Any?) { log(Log.INFO, format, *args) }

    override fun warn(msg: String) { log(Log.WARN, msg) }
    override fun warn(msg: String, t: Throwable) { log(Log.WARN, msg, t) }
    override fun warn(format: String, vararg args: Any?) { log(Log.WARN, format, *args) }

    override fun error(msg: String) { log(Log.ERROR, msg) }
    override fun error(msg: String, t: Throwable) { log(Log.ERROR, msg, t) }
    override fun error(format: String, vararg args: Any?) { log(Log.ERROR, format, *args) }
}

/**
 * Lightweight factory that mimics SLF4J's LoggerFactory#getLogger so changing the logger instance
 * requires only swapping the factory implementation.
 */
internal object LoggerFactory {
    fun getLogger(name: String? = null, minLevel: LogLevel = LogLevel.DEBUG): Logger = AndroidLogger(name, minLevel)
}
