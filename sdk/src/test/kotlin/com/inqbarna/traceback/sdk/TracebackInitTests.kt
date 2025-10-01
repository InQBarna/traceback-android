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

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inqbarna.traceback.sdk.base.BaseTracebackTest
import com.inqbarna.traceback.sdk.base.TRACEBACK_DOMAIN
import com.inqbarna.traceback.sdk.base.TRACEBACK_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
internal class TracebackInitTests : BaseTracebackTest() {

    @Test
    fun `init with no metadata throws for missing domain`() {
        initializeTraceback {
            /* no-op */
        }

        // But resolving a pending link should fail due to missing domain
        runBlocking {
            val intent = Intent()
            val domainResult = runCatching { Traceback.config.tracebackDomain }
            expect.withMessage("result.isFailure").that(domainResult.isFailure).isTrue()
            expect.withMessage("result.exception.message").that(domainResult.exceptionOrNull()?.message).startsWith("No domain attribute found in the application metadata.")
        }
    }

    @Test
    fun `init with no metadata throws for missing version`() {
        initializeTraceback {
            /* no-op */
        }

        // But resolving a pending link should fail due to missing domain
        runBlocking {
            val intent = Intent()
            val versionResult = runCatching { Traceback.config.tracebackVersion }
            expect.withMessage("result.isFailure").that(versionResult.isFailure).isTrue()
            expect.withMessage("result.exception.message").that(versionResult.exceptionOrNull()?.message).startsWith("No version attribute found in the application metadata, make sure you are not removing metadata node")
        }
    }

    @Test
    fun `init with domain and version and provider instantiates provider`() {
        // Set required metadata

        var providerConfigured = false
        val provider = object : TracebackConfigProvider {
            override val configure: TracebackConfigBuilder.() -> Unit = {
                providerConfigured = true
                minMatchType(MatchType.Unique)
            }
        }

        initializeTraceback(provider) {
            configureDomainAndVersion()
        }

        expect.that(providerConfigured).isTrue()
        expect.that(Traceback.config.tracebackVersion).isEqualTo(TRACEBACK_VERSION)
        expect.that(Traceback.config.tracebackDomain).isEqualTo(TRACEBACK_DOMAIN)
        expect.that(Traceback.config.minMatchType).isEqualTo(InternalMatchType.Unique)
    }

    @Test
    fun `init with domain and version and no provider uses defaults`() {

        initializeTraceback {
            configureDomainAndVersion()
        }

        // Provider was not configured
        expect.that(Traceback.config.minMatchType).isEqualTo(InternalMatchType.Ambiguous)
    }
}
