package com.inqbarna.traceback.sdk

import android.annotation.SuppressLint
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.edit
import androidx.core.content.getSystemService
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
private val logger by lazy { LoggerFactory.getLogger("Traceback") }

/**
 * This function is used to proceed with the link resolution process without waiting for focus gain.
 * It is useful in scenarios where you want to ignore focus changes and proceed immediately. In this scenario
 * the clipboard access won't be valid so can use it to enhance resolution heuristics.
 */
fun proceedIgnoringFocus(): Flow<Boolean> = flowOf(true)

private const val KeyTracebackLinkResolved = "traceback_link_resolved"

@SuppressLint("StaticFieldLeak")
object Traceback {
    private const val DEEPLINK_DOMAIN_ATTRIBUTE = "com.inqbarna.traceback.domain"

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

    private suspend fun openPrefs(): SharedPreferences {
        return withContext(Dispatchers.IO) {
            appContext.getSharedPreferences("traceback_prefs", Context.MODE_PRIVATE).also {
                logger.info("Opened SharedPreferences for Traceback SDK")
            }
        }
    }

    private suspend fun <T> SharedPreferences.readPrefs(block: SharedPreferences.() -> T): T {
        return withContext(Dispatchers.IO) {
            block(this@readPrefs)
        }
    }

    private suspend fun <T> SharedPreferences.editPrefs(block: SharedPreferences.Editor.() -> T) {
        return withContext(Dispatchers.IO) {
            edit {
                block(this)
            }
        }
    }

    /**
     * Resolves a pending traceback link from the given intent. This function should be called when app is launched.
     *
     * The method will try to resolve valid link from the {Intent.data} itself, otherwise it will try to get the install referrer,
     * and as final fallback we will try to reach the traceback server using
     * heuristics based on the device information and clipboard content.
     *
     * @param intent The intent that launched the app
     * @param focusGainSignal A flow that emits a signal when the app gains focus. This is used to ensure that clipboard access is valid.
     */
    suspend fun resolvePendingTracebackLink(intent: Intent, focusGainSignal: Flow<Boolean> = proceedIgnoringFocus()): Result<Uri> {
        val data = intent.data
        logger.info("Resolving pending traceback link: $data")

        val packageManager = appContext.packageManager
        val info = packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_META_DATA)
        val updatedAt = Instant.ofEpochMilli(info.firstInstallTime)

        val domain = info.applicationInfo?.metaData?.getString(DEEPLINK_DOMAIN_ATTRIBUTE) ?: run {
            logger.warn("No domain attribute found in the application metadata")
            return Result.failure(IllegalArgumentException("No domain attribute found in the application metadata. Please add `<meta-data android:name=\"${DEEPLINK_DOMAIN_ATTRIBUTE}\" android:value=\"your.domain.com\" />` to your AndroidManifest.xml"))
        }

        logger.info("Using domain: $domain for link resolution")

        data?.getQueryParameter("link").let { link ->
            return if (link.isNullOrEmpty()) {
                logger.warn("No link parameter found in the intent data")
                val prefs = openPrefs()
                if (updatedAt.isBefore(Instant.now().minus(Duration.ofMinutes(30))) || prefs.readPrefs { getBoolean(KeyTracebackLinkResolved, false) }) {
                    logger.info("App was updated more than 30 minutes ago, won't try to get install referrer")
                    Result.failure(IllegalArgumentException("No link parameter found in the intent data"))
                } else {
                    logger.info("App was updated less than 30 minutes ago, good to try getting install referrer")
                    getInstallReferrer()
                        .recoverCatching {
                            logger.info("Using default heuristics to resolve link for domain: $domain")
                            useDefaultHeuristics(domain, updatedAt, focusGainSignal).getOrThrow()
                        }
                        .onSuccess {
                            prefs.editPrefs { putBoolean(KeyTracebackLinkResolved, true) }
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

    private suspend fun useDefaultHeuristics(
        domain: String,
        updatedAt: Instant,
        focusGainSignal: Flow<Boolean>
    ): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val client = HttpClient(OkHttp) {
                    defaultRequest {
                        url("https://$domain")
                    }
                    install(ContentNegotiation) {
                        json(
                            Json {
                                ignoreUnknownKeys = true
                                explicitNulls = false
                            }
                        )
                    }
                }

                withTimeoutOrNull(1.seconds) {
                    // await app in focus before proceeding to ensure clipboard access is valid
                    focusGainSignal.first { true }
                }

                val clipboardManager = appContext.getSystemService<ClipboardManager>()!!
                val clipboardUri = if (clipboardManager.hasPrimaryClip() && clipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                    val clip = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
                    if (!clip.isNullOrEmpty()) {
                        logger.info("Using clipboard content as link: $clip")
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

                val heuristics = loadHeuristicsFromWebView()
                val appLocale = appContext.resources.configuration.locales[0]
                val displayMetrics = appContext.resources.displayMetrics
                val requestPayload = DeviceFingerprint(
                    appInstallationTime = updatedAt.toEpochMilli(),
                    osVersion = Build.VERSION.RELEASE,
                    bundleId = appContext.packageName,
                    sdkVersion = "1.0.0",
                    uniqueMatchLinkToCheck = clipboardUri,
                    device = DeviceInfo(
                        deviceModelName = Build.MODEL,
                        languageCode = appLocale.toLanguageTag(),
                        languageCodeFromWebView = heuristics.language ?: "unknown",
                        appVersionFromWebView = heuristics.appVersion ?: appContext.packageManager.getPackageInfo(appContext.packageName, 0)?.versionName ?: "unknown",
                        languageCodeRaw = appLocale.toLanguageTag().replace("-", "_"),
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
                    val responseData = response.body<DeeplinkResponse>()
                    val deepLinkId = responseData.deepLinkId?.toHttpUrlOrNull()?.queryParameter("link")
                    if (!deepLinkId.isNullOrEmpty()) {
                        logger.info("Successfully resolved link with heuristics: $deepLinkId")
                        deepLinkId.toUri()
                    } else {
                        throw Exception("No deep link ID found in the response")
                    }
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
                    if (!cont.isActive) {
                        return
                    }
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
                            cont.resume(Result.failure(Exception("Failed to get install referrer, response code: $responseCode")))
                        }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    if (!cont.isActive) {
                        return
                    }
                    logger.warn("Install referrer service disconnected")
                    cont.resume(Result.failure(Exception("Install referrer service disconnected")))
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
    val events: Flow<JsHeuristics> = _events.asSharedFlow()

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
