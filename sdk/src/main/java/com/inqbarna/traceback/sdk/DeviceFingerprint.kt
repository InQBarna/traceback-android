package com.inqbarna.traceback.sdk

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 18/6/25
 */

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
    val sdkVersion: String = "1.0.0",
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

/*
{
  appInstallationTime: Date.now(), // del sistema de ficheros
  bundleId: 'com.test.app', // de nativo
  osVersion: '17.4', // de nativo
  sdkVersion: '1.0.0', // esto da igual,,,, es para info
  uniqueMatchLinkToCheck: 'http://127.0.0.1:5002/xxx?_lang=en-EN&_langs=en-EN&_tz=Europe%2FMadrid&_res=2560x1440&_dpr=1&_plt=MacIntel', // del clipboard nativo
  device: {
    deviceModelName: 'iPhone15,3', // del sistema
    languageCode: 'en-EN', // del sistema
    languageCodeFromWebView: 'en-EN', // del webview  generateDeviceLanguage
    appVersionFromWebView: '17.4',  // del webview  generateDeviceAppVersion
    languageCodeRaw: 'en_EN', // replacement "-" "_"
    screenResolutionWidth: 390, // del sistema
    screenResolutionHeight: 844, // del sistema
    timezone: 'Europe/London' // del sistema
  }
}
 */
