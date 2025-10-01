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

import android.content.Context
import com.inqbarna.traceback.sdk.AnalyticClient
import com.inqbarna.traceback.sdk.InternalMatchType
import com.inqbarna.traceback.sdk.MatchType
import com.inqbarna.traceback.sdk.MissingMetadataException
import com.inqbarna.traceback.sdk.ResolveParameters
import com.inqbarna.traceback.sdk.ResolveSource
import com.inqbarna.traceback.sdk.TracebackConfigBuilder
import com.inqbarna.traceback.sdk.TracebackConfigProvider
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 18/9/25
 */

internal fun createConfiguration(context: Context, provider: TracebackConfigProvider?, domainProvider: () -> String, versionProvider: () -> String): TracebackConfig {
    var matchType = InternalMatchType.Ambiguous
    var analyticClient: AnalyticClient = DisabledAnalyticClient()
    if (provider != null) {
        val builder = object : TracebackConfigBuilder {
            override val context: Context = context

            override fun minMatchType(type: MatchType) {
                matchType = when (type) {
                    MatchType.None -> InternalMatchType.None
                    MatchType.Ambiguous -> InternalMatchType.Ambiguous
                    MatchType.Heuristics -> InternalMatchType.Heuristics
                    MatchType.Unique -> InternalMatchType.Unique
                }
            }

            override fun setAnalyticClient(client: AnalyticClient) {
                analyticClient = client
            }
        }
        provider.configure(builder)
    }
    return TracebackConfig(matchType, analyticClient, versionProvider, domainProvider)
}

internal class TracebackConfig(
    val minMatchType: InternalMatchType,
    val analyticClient: AnalyticClient,
    tracebackVersionProvider: () -> String,
    tracebackDomainProvider: () -> String
) {
    val tracebackDomain: String by replayError(tracebackDomainProvider)
    val tracebackVersion: String by replayError(tracebackVersionProvider)
}

private fun <T> replayError(provider: () -> T): PropertyDelegateProvider<Any, ReadOnlyProperty<Any, T>> {
    return ReplayErrorDelegateProvider(provider)
}

private class ReplayErrorPropertyDelegate<T>(
    private val result: Result<T>
) : ReadOnlyProperty<Any, T> {

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return result.getOrElse { throwable ->
            throw MissingMetadataException(throwable.message ?: "missing configuration", throwable)
        }
    }
}

private class ReplayErrorDelegateProvider<T>(private val provider: () -> T) : PropertyDelegateProvider<Any, ReadOnlyProperty<Any, T>> {
    override fun provideDelegate(
        thisRef: Any,
        property: KProperty<*>
    ): ReadOnlyProperty<Any, T> {
        return ReplayErrorPropertyDelegate(
            runCatching {
                provider()
            }
        )
    }
}

private class DisabledAnalyticClient : AnalyticClient {
    override fun onResolveSource(source: ResolveSource, parameters: ResolveParameters) {
        /* no-op */
    }

    override fun onResolveFail(source: ResolveSource, parameters: ResolveParameters) {
        /* no-op */
    }
}
