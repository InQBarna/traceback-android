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

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 18/6/25
 */

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JsHeuristics(
    val language: String? = null,
    val languages: List<String>? = null,
    val timezone: String? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val devicePixelRatio: Double? = null,
    val platform: String? = null,
    val userAgent: String? = null,
    val connectionType: String? = null,
    val hardwareConcurrency: Int? = null,
    val memory: Double? = null,
    val colorDepth: Int? = null,
    val appVersion: String? = null,
)

@Serializable
internal data class DeviceFingerprint(
    val appInstallationTime: Long,
    val osVersion: String,
    val bundleId: String,
    val sdkVersion: String,
    val uniqueMatchLinkToCheck: String? = null,
    val device: DeviceInfo
)

@Serializable
internal data class DeviceInfo(
    val deviceModelName: String,
    val languageCode: String,
    val languageCodeFromWebView: String,
    val appVersionFromWebView: String,
    val languageCodeRaw: String,
    val screenResolutionWidth: Int,
    val screenResolutionHeight: Int,
    val timezone: String
)

@Serializable
internal data class DeeplinkResponse(
    @SerialName("match_type") val matchType: String,
    @SerialName("request_ip_version") val requestIpVersion: String,
    @SerialName("match_message") val matchMessage: String? = null,
    @SerialName("deep_link_id") val deepLinkId: String? = null,
)

internal fun MatchType.Companion.fromNetwork(str: String): MatchType {
    return when (str) {
        "unique" -> MatchType.Unique
        "ambiguous" -> MatchType.Ambiguous
        "none" -> MatchType.None
        else -> {
            throw IllegalArgumentException("Unknown match type received")
        }
    }
}

