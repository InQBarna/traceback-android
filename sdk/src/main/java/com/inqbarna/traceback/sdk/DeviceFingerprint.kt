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

