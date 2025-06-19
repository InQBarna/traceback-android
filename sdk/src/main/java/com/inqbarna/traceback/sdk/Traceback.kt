package com.inqbarna.traceback.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
private val logger by lazy { LoggerFactory.getLogger("Traceback") }

@SuppressLint("StaticFieldLeak")
object Traceback {
    const val DEEPLINK_DOMAIN_ATTRIBUTE = "traceback_domain"

    private lateinit var appContext: Context
    private lateinit var webView: WebView
    private val appToJsConnector = AppToJavascriptConnector()
    @SuppressLint("SetJavaScriptEnabled")
    internal fun init(context: Context) {
        appContext = context.applicationContext
        webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(appToJsConnector, "AndroidInterface")
            loadUrl("file:///android_asset/html/index.html")
            webChromeClient = object : android.webkit.WebChromeClient() {

                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        logger.info("WebView Console: ${it.message()} at line ${it.lineNumber()} in ${it.sourceId()}")
                    }
                    return true
                }
            }
        }
        logger.info("Traceback SDK initialized with context: ${appContext.packageName}")
    }

    suspend fun resolvePendingTracebackLink(intent: Intent): Result<Uri> {
        val data = intent.data
        logger.info("Resolving pending traceback link: $data")

        val packageManager = appContext.packageManager
        val info = packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_META_DATA)
        val updatedAt = Instant.ofEpochMilli(info.lastUpdateTime)

        val domain = info.applicationInfo.metaData.getString(DEEPLINK_DOMAIN_ATTRIBUTE) ?: run {
            logger.warn("No domain attribute found in the application metadata")
            return Result.failure(IllegalArgumentException("No domain attribute found in the application metadata. Pleas add `<meta-data android:name=\"${DEEPLINK_DOMAIN_ATTRIBUTE}\" android:value=\"your.domain.com\" />` to your AndroidManifest.xml"))
        }

        logger.info("Using domain: $domain for link resolution")

        data?.getQueryParameter("link").let { link ->
            return if (link.isNullOrEmpty()) {
                logger.warn("No link parameter found in the intent data")
                if (updatedAt.isBefore(Instant.now().minus(Duration.ofMinutes(30)))) {
                    logger.info("App was updated more than 30 minutes ago, won't try to get install referrer")
                    Result.failure(IllegalArgumentException("No link parameter found in the intent data"))
                } else {
                    logger.info("App was updated less than 30 minutes ago, good to try getting install referrer")
                    getInstallReferrer()
                        .recoverCatching {
                            logger.info("Using default heuristics to resolve link for domain: $domain")
                            useDefaultHeuristics(domain, updatedAt).getOrThrow()
                        }
                }
            } else {
                kotlin.runCatching {
                    link.toUri().also {
                        logger.info("Resolved link: $it")
                    }
                }
            }
        }
    }

    private suspend fun useDefaultHeuristics(domain: String, updatedAt: Instant): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val client = HttpClient(OkHttp) {
                    defaultRequest {
                        url("https://$domain")
                    }
                    install(ContentNegotiation) {
                        json(
                            Json {
                                encodeDefaults = true
                                ignoreUnknownKeys = true
                                prettyPrint = true
                            }
                        )
                    }
                }

                val heuristics = loadHeuristicsFromWebView()
                val appLocale = appContext.resources.configuration.locales[0]
                val displayMetrics = appContext.resources.displayMetrics
                val requestPayload = DeviceFingerprint(
                    appInstallationTime = updatedAt.toEpochMilli(),
                    osVersion = Build.VERSION.RELEASE,
                    sdkVersion = "1.0.0",
                    uniqueMatchLinkToCheck = null,
                    device = DeviceInfo(
                        deviceModelName = Build.MODEL,
                        languageCode = appLocale.language,
                        languageCodeFromWebView = heuristics.language ?: "unknown",
                        appVersionFromWebView = heuristics.appVersion ?: appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName,
                        languageCodeRaw = appLocale.language.replace("-", "_"),
                        screenResolutionWidth = heuristics.screenWidth ?: displayMetrics.widthPixels,
                        screenResolutionHeight = heuristics.screenHeight ?: displayMetrics.heightPixels,
                        timezone = heuristics.timezone ?: ZoneId.systemDefault().toString()
                    )
                )

                val response = client.post {
                    url("v1_postinstall_search_link")
                    contentType(ContentType.Application.Json)
                    setBody(requestPayload)
                }

                if (response.status.isSuccess()) {
                    val responseUri = response.body<String>().toUri()
                    logger.info("Successfully resolved link with heuristics: $responseUri")
                    responseUri
                } else {
                    logger.error("Failed to resolve link with heuristics, status: ${response.status}")
                    throw Exception("Failed to resolve link with heuristics, status: ${response.status}")
                }
            }
        }
    }

    private suspend fun loadHeuristicsFromWebView(): JsHeuristics {
        // Call the JS method
        return withContext(Dispatchers.Main) {
            webView.evaluateJavascript("window.collectHeuristics()", null)
            appToJsConnector.events.first()
        }
    }

    private suspend fun getInstallReferrer(): Result<Uri> = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(appContext).build()
        client.startConnection(
            object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            val referrerUri = client.installReferrer.installReferrer
                            val finalUri = referrerUri.toUri()
                            if (finalUri.scheme != "https") {
                                logger.info("Unexpected referrer: $finalUri")
                                cont.resume(Result.failure(Exception("Unexpected referrer, won't handle")))
                            } else {
                                logger.info("Install referrer received: $finalUri")
                                cont.resume(Result.success(finalUri))
                            }
                        }
                        else -> {
                            logger.error("Failed to get install referrer, response code: $responseCode")
                            cont.resumeWithException(Exception("Failed to get install referrer, response code: $responseCode"))
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    logger.warn("Install referrer service disconnected")
                    cont.cancel(Exception("Install referrer service disconnected"))
                }
            }
        )

        cont.invokeOnCancellation {
            try {
                client.endConnection()
                logger.info("Install referrer client connection ended")
            } catch (e: Exception) {
                logger.error("Error ending install referrer client connection", e)
            }
        }
    }
}

private class AppToJavascriptConnector {
    private val _events = MutableSharedFlow<JsHeuristics>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    internal val events: Flow<JsHeuristics> = _events.asSharedFlow()

    @JavascriptInterface
    fun receiveHeuristics(heuristics: String) {
        logger.info("Received heuristics from JavaScript: $heuristics")
        runCatching {
            val jsHeuristics = Json.decodeFromString<JsHeuristics>(heuristics)
            _events.tryEmit(jsHeuristics)
        }.onFailure {
            logger.error("Error processing heuristics from JavaScript", it)
        }
    }
}
