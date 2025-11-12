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

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Correspondence
import com.inqbarna.traceback.sdk.base.BaseTracebackTest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for Traceback.resolvePendingTracebackLink
 */
@RunWith(AndroidJUnit4::class)
internal class TracebackResolveTest : BaseTracebackTest() {
    
    companion object {
        const val CAMPAIGN_URL = "https://traceback.com/halloween"
        const val POST_INSTALL_DEEPLINK = "https://example.com/welcome"
        const val INSTALL_REFERRER_OLD_SCHOOL = "https://example.com/oldschool"
        const val INSTALL_REFERRER_CAMPAIGN = "$CAMPAIGN_URL?link=$POST_INSTALL_DEEPLINK"
        const val CLIPBOARD_UNIQUE_LINK = "https://traceback.com/?unique_link_to_check=$POST_INSTALL_DEEPLINK"
        const val REGULAR_LINK_WITH_NO_CAMPAIGN = "https://traceback.com/?link=$POST_INSTALL_DEEPLINK"
    }

    @Test
    fun `if intent conforms to campaign, referrer not called, but post-install is`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val postInstallConfig = networkConfig { respondPostInstallSuccess("ambiguous", POST_INSTALL_DEEPLINK) }

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent().also { it.data = CAMPAIGN_URL.toUri() })
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify { installReferrerProvider wasNot Called }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link")
    }

    @Test
    fun `when intent is not given, referrer is checked and post-install called`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val postInstallConfig = networkConfig { respondPostInstallSuccess("unique", POST_INSTALL_DEEPLINK) }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.success(CAMPAIGN_URL.toUri())

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link")
    }

    @Test
    fun `given a referrer and post-install failure, we get still get deeplink`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val postInstallConfig = networkConfig { request ->
            when (request.url.encodedPath) {
                "/v1_postinstall_search_link" -> respondFailure()
                "/v1_get_campaign" -> respondCampaignSuccess(POST_INSTALL_DEEPLINK)
                else -> respondFailure()
            }
        }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.success(CAMPAIGN_URL.toUri())

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link", "/v1_get_campaign").inOrder()
    }

    @Test
    fun `give we've already called post install search, campaign is properly called to resolve a campaign`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = true,
            postInstallChecked = true,
            campaigns = setOf("somecampaign")
        )

        val postInstallConfig = networkConfig { request ->
            respondCampaignSuccess(POST_INSTALL_DEEPLINK)
        }

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent().also { it.data = CAMPAIGN_URL.toUri() })
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 0) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 0) { editor.putBoolean(any(), any()) }
        val campaigns = mutableListOf<Set<String>>()
        coVerify(exactly = 1) { editor.putStringSet("traceback_reported_campaigns", capture(campaigns)) }
        expect.that(campaigns.first()).containsExactly("halloween", "somecampaign")

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_get_campaign")
    }

    @Test
    fun `given a referrer, post-install intent match type just succeeds without calling get_campaign`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = setOf("somecampaign")
        )

        val config = object : TracebackConfigProvider {
            override val configure: TracebackConfigBuilder.() -> Unit = {
                minMatchType(MatchType.Unique)
            }
        }

        val postInstallConfig = networkConfig { respondPostInstallSuccess("intent", POST_INSTALL_DEEPLINK, campaignId = "halloween") }

        val clipboardManager = ApplicationProvider.getApplicationContext<Application>().getSystemService<ClipboardManager>()!!
        clipboardManager.setPrimaryClip(ClipData.newPlainText("label", CLIPBOARD_UNIQUE_LINK))

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.success(INSTALL_REFERRER_CAMPAIGN.toUri())

        val engine = initializeTraceback(configProvider = config, networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }
        val campaigns = mutableListOf<Set<String>>()
        coVerify(exactly = 1) { editor.putStringSet("traceback_reported_campaigns", capture(campaigns)) }
        expect.that(campaigns.first()).containsExactly("halloween", "somecampaign")

        val request = engine!!.getRequestBody()
        expect.that(request!!).doesNotContainKey("uniqueMatchLinkToCheck")

        expect.that(engine.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link")
    }

    private suspend fun MockEngine.getRequestBody(idx: Int = 0): JsonObject? {
        val request = requestHistory.getOrNull(idx) ?: return null
        val bodyString = request.body.toByteArray().inputStream()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        return json.decodeFromStream(bodyString)
    }

    @Test
    fun `when old-school referrer is used, we still call post-install search and get proper link`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val postInstallConfig = networkConfig { respondPostInstallSuccess("unique", POST_INSTALL_DEEPLINK) }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.success(INSTALL_REFERRER_OLD_SCHOOL.toUri())

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(INSTALL_REFERRER_OLD_SCHOOL)

        coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link")
    }

    @Test
    fun `when we have deeplink as input, we successfully get it, post-install called but result is ignored`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val postInstallConfig = networkConfig { respondPostInstallSuccess("unique", "https://somethigelse.com") }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.success(REGULAR_LINK_WITH_NO_CAMPAIGN.toUri())

        val engine = initializeTraceback(networkMock = postInstallConfig) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
        expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

        coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
        coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
        coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }
        coVerify(exactly = 0) { editor.putStringSet("traceback_reported_campaigns", any()) }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
            .containsExactly("/v1_postinstall_search_link")
    }

    @Test
    fun `post-install is called even when no referral or intent`() {
        runTest {
            val (prefs, editor) = prepareMockSettings(
                referralChecked = false,
                postInstallChecked = false,
                campaigns = emptySet()
            )

            val config = object : TracebackConfigProvider {
                override val configure: TracebackConfigBuilder.() -> Unit = {
                    minMatchType(MatchType.Unique)
                }
            }

            val clipboardManager = ApplicationProvider.getApplicationContext<Application>().getSystemService<ClipboardManager>()!!
            clipboardManager.setPrimaryClip(ClipData.newPlainText("label", CLIPBOARD_UNIQUE_LINK))

            coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.failure(Exception("No referrer available"))

            val postInstallConfig = networkConfig { respondPostInstallSuccess("unique", POST_INSTALL_DEEPLINK) }

            val engine = initializeTraceback(networkMock = postInstallConfig, configProvider = config) {
                configureDomainAndVersion()
            }

            val result = Traceback.resolvePendingTracebackLink(Intent())
            expect.withMessage("result.isSuccess").that(result.isSuccess).isTrue()
            expect.withMessage("result.value").that(result.getOrNull().toString()).isEqualTo(POST_INSTALL_DEEPLINK)

            coVerify(exactly = 1) { installReferrerProvider.resolveInstallReferrer() }
            coVerify(exactly = 1) { editor.putBoolean("traceback_referral_queried", true) }
            coVerify(exactly = 1) { editor.putBoolean("traceback_postinstall_search_executed", true) }

            val request = engine!!.getRequestBody()
            expect.withMessage("request.uniqueMatchLinkToCheck").that(request!!.getValue("uniqueMatchLinkToCheck").jsonPrimitive.contentOrNull).isEqualTo(CLIPBOARD_UNIQUE_LINK)

            expect.that(engine.requestHistory).comparingElementsUsing<HttpRequestData, String>(Correspondence.transforming( { request -> request.url.encodedPath }, "has path"))
                .containsExactly("/v1_postinstall_search_link")
        }
    }

    @Test
    fun `post-install fails below configured match type`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )

        val configProvider = object : TracebackConfigProvider {
            override val configure: TracebackConfigBuilder.() -> Unit = {
                minMatchType(MatchType.Unique)
            }
        }

        coEvery { installReferrerProvider.resolveInstallReferrer() } returns Result.failure(
            Exception("No referrer available")
        )
        val postInstallConfig =
            networkConfig { respondPostInstallSuccess("ambiguous", POST_INSTALL_DEEPLINK) }

        val engine = initializeTraceback(
            configProvider = configProvider,
            networkMock = postInstallConfig
        ) {
            configureDomainAndVersion()
        }

        val result = Traceback.resolvePendingTracebackLink(Intent())
        expect.withMessage("result.isFailure").that(result.isFailure).isTrue()
        result.exceptionOrNull()?.printStackTrace()

        coVerify(exactly = 1) {
            editor.putBoolean(
                "traceback_postinstall_search_executed",
                true
            )
        }

        expect.that(engine!!.requestHistory).comparingElementsUsing<HttpRequestData, String>(
            Correspondence.transforming(
                { request -> request.url.encodedPath },
                "has path"
            )
        )
            .containsExactly("/v1_postinstall_search_link")
    }

    @Test
    fun `when version is not configured resolve fails appropriately`() = runTest {
        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )
        initializeTraceback {
            configureDomainAndVersion(version = null)
        }

        val result = Traceback.resolvePendingTracebackLink(Intent().also { it.data = CAMPAIGN_URL.toUri() })
        expect.withMessage("result.isFailure").that(result.isFailure).isTrue()
        expect.withMessage("result.exception.message").that(result.exceptionOrNull()?.message).startsWith("No version attribute found in the application metadata, make sure you are not removing metadata node")
    }

    @Test
    fun `when domain is not configured resolve fails appropriately`() = runTest {

        val (prefs, editor) = prepareMockSettings(
            referralChecked = false,
            postInstallChecked = false,
            campaigns = emptySet()
        )
        initializeTraceback {
            configureDomainAndVersion(domain = null)
        }

        val result = Traceback.resolvePendingTracebackLink(Intent().also { it.data = CAMPAIGN_URL.toUri() })
        expect.withMessage("result.isFailure").that(result.isFailure).isTrue()
        expect.withMessage("result.exception.message").that(result.exceptionOrNull()?.message).startsWith("No domain attribute found in the application metadata.")
    }


    private fun prepareMockSettings(referralChecked: Boolean = false, postInstallChecked: Boolean = false, campaigns: Set<String> = emptySet()): Pair<SharedPreferences, SharedPreferences.Editor> {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.getBoolean("traceback_referral_queried", any()) } returns referralChecked
        every { prefs.getBoolean("traceback_postinstall_search_executed", any()) } returns postInstallChecked
        every { prefs.getStringSet("traceback_reported_campaigns", any()) } returns campaigns
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        coEvery { preferenceProvider.openPrefs() } returns prefs
        return Pair(prefs, editor)
    }
}



private fun networkConfig(responses: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): MockEngineConfig.() -> Unit {
    return {
        addHandler { request ->
            responses(this, request)
        }
    }
}

private fun MockRequestHandleScope.respondFailure(): HttpResponseData {
    val body = """ { "error": "Not found" } """
    return respond(body, HttpStatusCode.NotFound, headersOf("Content-Type", "application/json"))
}

private fun MockRequestHandleScope.respondPostInstallSuccess(
    matchType: String,
    deeplinkId: String,
    campaignId: String? = null
): HttpResponseData {
    val body = """
                    { "match_type": "$matchType", "request_ip_version": "v4", "deep_link_id": "$deeplinkId", "match_campaign": ${if (campaignId != null) "\"$campaignId\"" else null} }
                """
    return respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
}

private fun MockRequestHandleScope.respondCampaignSuccess(postInstallDeeplink: String): HttpResponseData {
    val body = """
                    { "result": "$postInstallDeeplink" }
                """
    return respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
}
