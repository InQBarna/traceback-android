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

import android.content.Context

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 18/9/25
 */
interface TracebackConfigProvider {
    val configure: ((TracebackConfigBuilder.() -> Unit))
}

interface TracebackConfigBuilder {
    val context: Context
    fun minMatchType(type: MatchType)
    fun setAnalyticClient(client: AnalyticClient)
}

enum class ResolveSource {
    Intent, Referrer, Heuristics
}

typealias ResolveParameters = Map<String, String>
fun ResolveParameters.toMap(): Map<String, String> = this

interface AnalyticClient {
    fun onResolveSource(source: ResolveSource, parameters: ResolveParameters)
    fun onResolveFail(source: ResolveSource, parameters: ResolveParameters)
}
