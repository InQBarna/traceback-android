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
import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.inqbarna.traceback.sdk.DeviceFingerprint
import com.inqbarna.traceback.sdk.DeviceInfo
import com.inqbarna.traceback.sdk.HeuristicsInfoProvider
import com.inqbarna.traceback.sdk.JsHeuristics
import com.inqbarna.traceback.sdk.Traceback.config
import com.inqbarna.traceback.sdk.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

internal class DefaultHeuristicsProvider(
    private val appContext: Context,
    private val jsCollector: JsHeuristicCollector = WebViewBasedJsHeuristicCollector(appContext),
) : HeuristicsInfoProvider {
    @SuppressLint("VisibleForTests")
    override suspend fun loadHeuristics(
        updatedAt: Instant,
        clipboardContentProvider: ClipboardContentProvider,
        intentLink: String?
    ): Result<DeviceFingerprint> {
        return runCatching {
            val clipboardUri = clipboardContentProvider.getClipboardLinkIfAvailable()
            logger.debug("Going to collect heuristics with clipboard content: $clipboardUri and intent link: $intentLink")
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
    private val context: Context,
) : JsHeuristicCollector {

    private val appToJsConnector = AppToJavascriptConnector()

    private var _webView: WebView? = null

    private suspend fun getWebView(): WebView {
        return _webView ?: initializeWebView().also {
            _webView = it
        }
    }

    private suspend fun initializeWebView(): WebView = suspendCancellableCoroutine { cont ->
        logger.debug("Initializing WebView for heuristics collection")
        WebView(context).apply {
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

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    logger.debug("WebView page finished loading: $url")
                    cont.resume(view)
                }
            }
        }
    }


    override suspend fun loadHeuristics(): JsHeuristics {
        return withContext(Dispatchers.Main) {
            withTimeout(2.seconds) {
                getWebView().evaluateJavascript("window.collectHeuristics()", null)
                appToJsConnector.events.first()
            }
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
