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

import android.net.Uri
import io.ktor.http.Url

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 3/11/25
 */
internal sealed class LinkKind() {
    abstract val original: Url
    data class CampaignLink(override val original: Url, val campaignId: String, val deeplink: Url? = null) : LinkKind()
    data class RegularDeeplink(override val original: Url, val deeplink: Url) : LinkKind()
    data class Unknown(override val original: Url) : LinkKind()


    internal companion object {
        fun fromUri(uri: Uri, tracebackDomain: String? = null): LinkKind {
            val originalUrl = Url(uri.toString())
            val campaignId = originalUrl.encodedPath.trim('/')
            if (tracebackDomain != null) {
                val hostMatches = originalUrl.host.equals(tracebackDomain, ignoreCase = true)
                if (!hostMatches) {
                    return Unknown(original = originalUrl)
                }
            }
            return if (campaignId.isNotEmpty()) {
                CampaignLink(
                    original = originalUrl,
                    campaignId = campaignId,
                    deeplink = originalUrl.parameters["link"]?.let { Url(it) }
                )
            } else if (originalUrl.parameters["link"] != null) {
                RegularDeeplink(
                    original = originalUrl,
                    deeplink = Url(originalUrl.parameters["link"]!!)
                )
            } else {
                Unknown(original = originalUrl)
            }
        }
    }
}

internal val LinkKind.deeplink: Url?
    get() = when (this) {
        is LinkKind.CampaignLink -> this.deeplink
        is LinkKind.RegularDeeplink -> this.deeplink
        is LinkKind.Unknown -> null
    }
