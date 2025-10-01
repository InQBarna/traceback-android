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

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.getSystemService
import com.inqbarna.traceback.sdk.DeviceFingerprint
import com.inqbarna.traceback.sdk.DeviceInfo
import com.inqbarna.traceback.sdk.HeuristicsInfoProvider
import com.inqbarna.traceback.sdk.InternalMatchType
import com.inqbarna.traceback.sdk.JsHeuristics
import com.inqbarna.traceback.sdk.MatchType
import com.inqbarna.traceback.sdk.Traceback.config
import com.inqbarna.traceback.sdk.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 31/10/25
 */
internal class DefaultHeuristicsProvider(
    private val appContext: Context,
    private val jsCollector: JsHeuristicCollector = WebViewBasedJsHeuristicCollector(appContext),
) : HeuristicsInfoProvider {
    @SuppressLint("VisibleForTests")
    override suspend fun loadHeuristics(
        updatedAt: Instant,
        focusGainSignal: Flow<Boolean>,
        intentLink: String?
    ): Result<DeviceFingerprint> {
        return runCatching {
            val clipboardUri = if (config.minMatchType == InternalMatchType.Unique) {
                withTimeoutOrNull(1.seconds) {
                    // await app in focus before proceeding to ensure clipboard access is valid
                    focusGainSignal.first { true }
                }

                val clipboardManager = appContext.getSystemService<ClipboardManager>()!!
                if (
                    clipboardManager.hasPrimaryClip()
                    && clipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                ) {
                    val clip =
                        clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
                    if (!clip.isNullOrEmpty()) {
                        logger.debug("Using clipboard content as link: $clip")
                        clip.toHttpUrlOrNull()?.toString()?.also {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                clipboardManager.clearPrimaryClip()
                            }
                        }
                    } else {
                        logger.warn("Clipboard content is empty, proceeding with heuristics")
                        null
                    }
                } else {
                    logger.info("No valid clipboard content found, proceeding with heuristics")
                    null
                }
            } else {
                logger.info("Won't use clipboard, match type is not unique")
                null
            }

            val heuristics = jsCollector.loadHeuristics()
            val appLocale = appContext.resources.configuration.locales[0]
            val displayMetrics = appContext.resources.displayMetrics
            DeviceFingerprint(
                appInstallationTime = updatedAt.toEpochMilli(),
                osVersion = Build.VERSION.RELEASE,
                bundleId = appContext.packageName,
                sdkVersion = "android/${config.tracebackVersion}",
                uniqueMatchLinkToCheck = clipboardUri,
                intentLink = intentLink,
                device = DeviceInfo(
                    deviceModelName = Build.MODEL,
                    languageCode = appLocale.toLanguageTag(),
                    languageCodeFromWebView = heuristics.language ?: "unknown",
                    appVersionFromWebView = heuristics.appVersion ?: appContext.packageManager.getPackageInfo(
                        appContext.packageName, 0)?.versionName ?: "unknown",
                    languageCodeRaw = appLocale.toLanguageTag().replace("-", "_"),
                    screenResolutionWidth = heuristics.screenWidth ?: displayMetrics.widthPixels,
                    screenResolutionHeight = heuristics.screenHeight ?: displayMetrics.heightPixels,
                    timezone = heuristics.timezone ?: ZoneId.systemDefault().toString()
                )
            )
        }
    }
}

private class WebViewBasedJsHeuristicCollector(
    context: Context,
) : JsHeuristicCollector {

    private val appToJsConnector = AppToJavascriptConnector()

    private val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(appToJsConnector, "AndroidInterface")
        loadUrl("file:///android_asset/html/index.html")
        webChromeClient = object : android.webkit.WebChromeClient() {

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    logger.debug("WebView Console: ${it.message()} at line ${it.lineNumber()} in ${it.sourceId()}")
                }
                return true
            }
        }
    }


    override suspend fun loadHeuristics(): JsHeuristics {
        return withContext(Dispatchers.Main) {
            webView.evaluateJavascript("window.collectHeuristics()", null)
            appToJsConnector.events.first()
        }
    }
}


internal interface JsHeuristicCollector {
    suspend fun loadHeuristics(): JsHeuristics
}

private class AppToJavascriptConnector {
    private val _events = MutableSharedFlow<JsHeuristics>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<JsHeuristics> = _events.asSharedFlow()

    @JavascriptInterface
    fun receiveHeuristics(heuristics: String) {
        logger.debug("Received heuristics from JavaScript: $heuristics")
        runCatching {
            val jsHeuristics = Json.decodeFromString<JsHeuristics>(heuristics)
            _events.tryEmit(jsHeuristics)
        }.onFailure {
            logger.error("Error processing heuristics from JavaScript", it)
        }
    }
}
