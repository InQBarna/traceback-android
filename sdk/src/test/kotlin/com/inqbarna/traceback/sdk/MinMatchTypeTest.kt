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
import android.net.Uri
import androidx.core.net.toUri
import com.google.common.truth.Correspondence
import com.inqbarna.traceback.sdk.TracebackResolveTest.Companion.CAMPAIGN_URL
import com.inqbarna.traceback.sdk.TracebackResolveTest.Companion.POST_INSTALL_DEEPLINK
import com.inqbarna.traceback.sdk.base.BaseTracebackTest
import com.inqbarna.traceback.sdk.util.networkConfig
import com.inqbarna.traceback.sdk.util.respondPostInstallSuccess
import io.ktor.client.request.HttpRequestData
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 12/11/25
 */

internal class MinMatchTypeTestParams(
    val minMatchType: MatchType,
    val responseCampaignId: String?,
    private val referrerResponseUrl: String?,
    private val intentUrl: String?,
) {
    val intent: Intent by lazy {
        Intent().apply {
            if (intentUrl != null) {
                data = intentUrl.toUri()
            }
        }
    }
    val referrerResponse: Result<Uri> by lazy {
        when (referrerResponseUrl) {
            null -> Result.failure(Exception("There's no referrer"))
            else -> Result.success(referrerResponseUrl.toUri())
        }
    }
    override fun toString(): String {
        // return a readable representation for the test name
        return buildString {
            append("Min MatchType: $minMatchType, and server campaign id = ${responseCampaignId ?: "no id"}")
            if (referrerResponseUrl != null) {
                append(", with referrer URI")
            } else {
                append(", without referrer")
            }
            if (intentUrl != null) {
                append(", and intent with data")
            } else {
                append(", and intent without data")
            }
        }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class MinMatchTypeTest(
    private val params: MinMatchTypeTestParams,
) : BaseTracebackTest() {

    @Test
    fun executeWithParameters() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val configProvider = object : TracebackConfigProvider {
            override val configure: TracebackConfigBuilder.() -> Unit = {
                minMatchType(params.minMatchType)
            }
        }

        val postInstallConfig =
            networkConfig {
                respondPostInstallSuccess(
                    "ambiguous",
                    POST_INSTALL_DEEPLINK,
                    campaignId = params.responseCampaignId
                )
            }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns params.referrerResponse

        val engine = initializeTraceback(
            configProvider = configProvider,
            networkMock = postInstallConfig
        ) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(params.intent)
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 1) {
            editor.putBoolean("traceback_postinstall_search_executed", true)
        }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(
            Correspondence.transforming(
                { request -> request.url.encodedPath },
                "has path"
            )
        ).containsExactly("/v1_postinstall_search_link")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: {0}")
        fun params(): List<Any> {
            return listOf(
                MinMatchTypeTestParams(
                    minMatchType = MatchType.Unique,
                    responseCampaignId = null,
                    referrerResponseUrl = CAMPAIGN_URL,
                    intentUrl = null
                ),
                MinMatchTypeTestParams(
                    minMatchType = MatchType.Unique,
                    responseCampaignId = null,
                    referrerResponseUrl = null,
                    intentUrl = CAMPAIGN_URL
                ),
                MinMatchTypeTestParams(
                    minMatchType = MatchType.Unique,
                    responseCampaignId = "halloween",
                    referrerResponseUrl = null,
                    intentUrl = null
                )
            )
        }
    }
}
