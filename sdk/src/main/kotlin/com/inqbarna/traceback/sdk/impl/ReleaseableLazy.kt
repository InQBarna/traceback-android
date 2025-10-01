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

package com.inqbarna.traceback.sdk.impl

import androidx.annotation.VisibleForTesting

/**
 * This whole class is a copy of Kotlin's Lazy implementation but adding a clear() method to release the value
 * useful for testing purposes.
 *
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 3/11/25
 */
private val UNINITIALIZED_VALUE = Any()
internal class ReleaseableLazy<T>(private val initializer: () -> T) : Lazy<T> {
    protected var _value: Any? = UNINITIALIZED_VALUE
    override val value: T
        get() {
            val res = _value
            if (res !== UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST")
                return res as T
            } else {
                synchronized(this) {
                    val res2 = _value
                    if (res2 !== UNINITIALIZED_VALUE) {
                        @Suppress("UNCHECKED_CAST")
                        return res2 as T
                    } else {
                        return initializer().also {
                            _value = it
                        }
                    }
                }
            }
        }

    @VisibleForTesting
    internal fun clear() {
        synchronized(this) {
            _value = UNINITIALIZED_VALUE
        }
    }

    override fun isInitialized(): Boolean {
        return _value !== UNINITIALIZED_VALUE
    }
}


internal fun <T> releaseableLazy(initializer: () -> T): ReleaseableLazy<T> = ReleaseableLazy(initializer)
