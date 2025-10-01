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

package com.inqbarna.traceback.sdk.base

import android.app.Application
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.ApplicationInfoBuilder
import com.google.common.truth.Expect
import com.inqbarna.traceback.sdk.InstallReferrerProvider
import com.inqbarna.traceback.sdk.JsHeuristics
import com.inqbarna.traceback.sdk.PreferencesProvider
import com.inqbarna.traceback.sdk.Traceback
import com.inqbarna.traceback.sdk.TracebackConfigProvider
import com.inqbarna.traceback.sdk.impl.DefaultHeuristicsProvider
import com.inqbarna.traceback.sdk.impl.JsHeuristicCollector
import com.inqbarna.traceback.sdk.rules.RobolectricLoggingRule
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.After
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal const val TRACEBACK_DOMAIN = "traceback.com"
internal const val TRACEBACK_VERSION = "1.2.3"

internal open class BaseTracebackTest {

    @get:Rule(0)
    val robolectricLogging = RobolectricLoggingRule()

    @get:Rule(1)
    val expect = Expect.create()


    protected val installReferrerProvider = mockk<InstallReferrerProvider>()
    protected val preferenceProvider = mockk<PreferencesProvider>()
    protected val heuristicsCollector = mockk<JsHeuristicCollector> {
        coEvery {
            loadHeuristics()
        } returns JsHeuristics()
    }
    protected val clock: Clock = Clock.fixed(Instant.parse("2025-09-15T07:00:00Z"), ZoneId.of("Europe/Madrid"))

    protected var engine: MockEngine? = null

    @After
    fun cleanup() {
        engine?.close()
        Traceback.clear()
    }

    protected fun Bundle.configureDomainAndVersion(
        domain: String? = TRACEBACK_DOMAIN,
        version: String? = TRACEBACK_VERSION
    ) {
        domain?.let {
            putString("com.inqbarna.traceback.domain", it)
        }
        version?.let {
            putString("com.inqbarna.traceback.sdk.version", it)
        }
    }

    protected fun initializeTraceback(
        configProvider: TracebackConfigProvider? = null,
        networkMock: (MockEngineConfig.() -> Unit)? = null,
        firstInstallInstant: Instant? = null,
        metadataBuilder: Bundle.() -> Unit
    ): MockEngine? {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val applicationInfo = ApplicationInfoBuilder.newBuilder()
            .setPackageName(context.packageName)
            .build()
            .also { applicationInfo ->
                if (applicationInfo.metaData == null) {
                    applicationInfo.metaData = Bundle()
                }
                metadataBuilder(applicationInfo.metaData!!)
            }

        (firstInstallInstant ?: clock.instant())?.let { installTime ->
            val shadowPackageManager = shadowOf(context.packageManager)
            shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
                .also {
                    it.firstInstallTime = installTime.toEpochMilli()
                }
        }

        engine = networkMock?.let { MockEngine.create(it) } as? MockEngine

        Traceback.init(
            context = context,
            appInfo = applicationInfo,
            configProvider = configProvider,
            installReferrerProvider = installReferrerProvider,
            clientEngine = engine ?: OkHttp.create(),
            preferencesProvider = preferenceProvider,
            heuristicsInfoProvider = DefaultHeuristicsProvider(context, heuristicsCollector),
            clock = clock
        )
        return engine
    }
}
