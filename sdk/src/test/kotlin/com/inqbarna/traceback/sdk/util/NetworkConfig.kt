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

package com.inqbarna.traceback.sdk.util

import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun networkConfig(responses: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): MockEngineConfig.() -> Unit {
    return {
        addHandler { request ->
            responses(this, request)
        }
    }
}

internal fun MockRequestHandleScope.respondFailure(): HttpResponseData {
    val body = """ { "error": "Not found" } """
    return respond(body, HttpStatusCode.Companion.NotFound,
        headersOf("Content-Type", "application/json")
    )
}

internal fun MockRequestHandleScope.respondPostInstallSuccess(
    matchType: String,
    deeplinkId: String,
    campaignId: String? = null
): HttpResponseData {
    val body = """
                    { "match_type": "$matchType", "request_ip_version": "v4", "deep_link_id": "$deeplinkId", "match_campaign": ${if (campaignId != null) "\"$campaignId\"" else null} }
                """
    return respond(body, HttpStatusCode.Companion.OK, headersOf("Content-Type", "application/json"))
}

internal fun MockRequestHandleScope.respondCampaignSuccess(postInstallDeeplink: String): HttpResponseData {
    val body = """
                    { "result": "$postInstallDeeplink" }
                """
    return respond(body, HttpStatusCode.Companion.OK, headersOf("Content-Type", "application/json"))
}
