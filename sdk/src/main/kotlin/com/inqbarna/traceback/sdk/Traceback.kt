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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.net.toUri
import com.inqbarna.traceback.sdk.impl.ClipboardContentProvider
import com.inqbarna.traceback.sdk.impl.DefaultHeuristicsProvider
import com.inqbarna.traceback.sdk.impl.DefaultInstallReferrerProvider
import com.inqbarna.traceback.sdk.impl.DefaultPreferenceProvider
import com.inqbarna.traceback.sdk.impl.LinkKind
import com.inqbarna.traceback.sdk.impl.NoMatchTypeExpectationsMatched
import com.inqbarna.traceback.sdk.impl.TracebackConfig
import com.inqbarna.traceback.sdk.impl.createConfiguration
import com.inqbarna.traceback.sdk.impl.deeplink
import com.inqbarna.traceback.sdk.impl.heuristicsParameters
import com.inqbarna.traceback.sdk.impl.obtainClipboardContentProvider
import com.inqbarna.traceback.sdk.impl.releaseableLazy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.reflect.full.createInstance

/**
 * @author David García (david.garcia@inqbarna.com)
 * @version 1.0 17/6/25
 */
private var DEBUG = false
internal val logger by lazy {
    LoggerFactory.getLogger(minLevel = if (DEBUG) LogLevel.DEBUG else LogLevel.INFO)
}

/**
 * This function is used to proceed with the link resolution process without waiting for focus gain.
 * It is useful in scenarios where you want to ignore focus changes and proceed immediately. In this scenario
 * the clipboard access won't be valid so can use it to enhance resolution heuristics.
 */
fun proceedIgnoringFocus(): Flow<Boolean> = flowOf(true)

private const val KeyTracebackReferralQueried = "traceback_referral_queried"
private const val KeyPostinstallSearchExecuted = "traceback_postinstall_search_executed"
private const val KeyCampaignsReported = "traceback_reported_campaigns"

@SuppressLint("StaticFieldLeak")
object Traceback {
    private const val DEEPLINK_DOMAIN_ATTRIBUTE = "com.inqbarna.traceback.domain"
    private const val VERSION_ATTRIBUTE = "com.inqbarna.traceback.sdk.version"
    private const val CONFIG_PROVIDER_ATTRIBUTE = "com.inqbarna.traceback.sdk.TracebackConfigProvider"

    private lateinit var appContext: Context

    @VisibleForTesting
    internal lateinit var config: TracebackConfig

    private lateinit var installReferrerProvider: InstallReferrerProvider
    private lateinit var clientEngine: HttpClientEngine
    private lateinit var preferencesProvider: PreferencesProvider
    private lateinit var clock: Clock

    private val httpClientDelegate = releaseableLazy {
        HttpClient(clientEngine) {
            defaultRequest {
                url("https://${config.tracebackDomain}")
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

    }
    private val httpClient: HttpClient by httpClientDelegate

    private lateinit var heuristicsInfoProvider: HeuristicsInfoProvider

    internal fun init(context: Context) {
        DEBUG = context.resources.getBoolean(R.bool.is_traceback_sdk_debug)
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val configProviderName = appInfo.metaData.getString(CONFIG_PROVIDER_ATTRIBUTE)
        val configProvider: TracebackConfigProvider? = if (configProviderName != null) {
            try {
                val providerClass = Class.forName(configProviderName).kotlin
                providerClass.createInstance() as TracebackConfigProvider
            } catch (e: Throwable) {
                logger.info("Failed to instantiate configuration provider '$configProviderName'", e)
                null
            }
        } else {
            logger.info("No configuration provider found, just using default settings")
            null
        }
        init(context, appInfo, configProvider)
    }

    @VisibleForTesting
    internal fun clear() {
        httpClientDelegate.clear()
    }

    @VisibleForTesting
    internal fun init(
        context: Context,
        appInfo: ApplicationInfo,
        configProvider: TracebackConfigProvider? = null,
        installReferrerProvider: InstallReferrerProvider = DefaultInstallReferrerProvider(context),
        heuristicsInfoProvider: HeuristicsInfoProvider = DefaultHeuristicsProvider(context),
        clientEngine: HttpClientEngine = OkHttp.create(),
        preferencesProvider: PreferencesProvider = DefaultPreferenceProvider(context),
        clock: Clock = Clock.systemUTC()
    ) {
        appContext = context.applicationContext
        DEBUG = context.resources.getBoolean(R.bool.is_traceback_sdk_debug)
        this.installReferrerProvider = installReferrerProvider
        this.heuristicsInfoProvider = heuristicsInfoProvider
        this.clientEngine = clientEngine
        this.preferencesProvider = preferencesProvider
        this.clock = clock

        config = createConfiguration(
            context = appContext,
            provider = configProvider,
            domainProvider = {
                appInfo.metaData.getString(DEEPLINK_DOMAIN_ATTRIBUTE)
                    .also {
                        logger.info("Using domain: $it for link resolution")
                    } ?: run {
                    logger.warn("No domain attribute found in the application metadata")
                    throw IllegalArgumentException("No domain attribute found in the application metadata. Please add `<meta-data android:name=\"${DEEPLINK_DOMAIN_ATTRIBUTE}\" android:value=\"your.domain.com\" />` to your AndroidManifest.xml")
                }
            },
            versionProvider = {
                appInfo.metaData.getString(VERSION_ATTRIBUTE) ?: throw IllegalStateException("No version attribute found in the application metadata, make sure you are not removing metadata node `<meta-data android:name=\"${VERSION_ATTRIBUTE}\"/>` with tool:replace or tool:remove in your AndroidManifest.xml")
            }
        )
        logger.info("Traceback SDK initialized in {} mode", if (DEBUG) "DEBUG" else "RELEASE")
    }

    private suspend fun openPrefs(): SharedPreferences {
        return preferencesProvider.openPrefs()
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

        val prefs = openPrefs()

        val domain = try {
            config.tracebackDomain
        } catch (e: Throwable) {
            logger.error("Failed to get traceback domain from configuration: ${e.message}", e)
            return Result.failure(e)
        }

        val intentOrReferral = intent.data
            ?.let { LinkKind.fromUri(it) }
            ?: getInstallReferrer(updatedAt)
                .map { LinkKind.fromUri(it, domain) }
                .onFailure {
                    logger.error("Failed to get install referrer: ${it.message}", it)
                }
                .getOrNull()


        return if (prefs.readPrefs { getBoolean(KeyPostinstallSearchExecuted, false) }) {
            logger.debug("Post-Install already executed, we need to just resolve campaign if needed")
            when (intentOrReferral) {
                is LinkKind.CampaignLink -> innerResolveCampaign(intentOrReferral, prefs)
                else -> resolveFinalDeeplink(intentOrReferral, null)
            }
        } else {
            // We need to execute post-install at least once
            val clipboardProvider = obtainClipboardContentProvider(focusGainSignal.takeIf { intentOrReferral?.deeplink == null }, appContext)
            resolvePostInstallHeuristics(updatedAt, clipboardProvider, intentOrReferral?.takeUnless { it is LinkKind.Unknown }?.original?.toString())
                .onSuccess {  response ->
                    response.campaignId?.let { campaignId ->
                        val campaigns = prefs.readPrefs { getStringSet(KeyCampaignsReported, emptySet())!!.toSet() }
                        prefs.editPrefs {
                            putStringSet(KeyCampaignsReported, campaigns.toMutableSet().also { it.add(campaignId) })
                        }
                    }
                }
                .mapCatching {
                    val proceed = when {
                        intentOrReferral is LinkKind.CampaignLink -> true
                        it.campaignId != null -> true
                        else -> when (it.matchType) {
                            InternalMatchType.Unique, InternalMatchType.Intent -> true
                            InternalMatchType.None -> false
                            else -> it.matchType >= config.minMatchType
                        }
                    }

                    if (!proceed) {
                        config.analyticClient.onResolveFail(
                            ResolveSource.Heuristics,
                            heuristicsParameters(
                                it.matchType,
                                it.clipboardUsed
                            )
                        )
                        throw NoMatchTypeExpectationsMatched("Response had match type ${it.matchType} which is not enough to proceed (expected ${config.minMatchType})")
                    } else {
                        config.analyticClient.onResolveSource(
                            ResolveSource.Heuristics,
                            heuristicsParameters(
                                it.matchType,
                                it.clipboardUsed
                            )
                        )
                    }
                    resolveFinalDeeplink(intentOrReferral, it).getOrThrow()
                }
                .recoverCatching {
                    when (it) {
                        is MissingMetadataException -> throw it
                        !is NoMatchTypeExpectationsMatched if intentOrReferral is LinkKind.CampaignLink -> {
                            // For errors other than no-match, try to resolve campaign link if available
                            innerResolveCampaign(intentOrReferral, prefs).getOrThrow()
                        }

                        else -> {
                            // otherwise just try to resolve final deeplink derived from intent/referral
                            logger.error("Heuristics resolution failed: ${it.message}", it)
                            resolveFinalDeeplink(intentOrReferral, null).getOrThrow()
                        }
                    }
                }.also {
                    // we've called heuristics, don't do it again even if it failed now
                    prefs.editPrefs {
                        putBoolean(KeyPostinstallSearchExecuted, true)
                    }
                }
        }
    }

    private suspend fun innerResolveCampaign(
        intentOrReferral: LinkKind.CampaignLink,
        prefs: SharedPreferences
    ): Result<Uri> {
        val campaignsReported = prefs.readPrefs { getStringSet(KeyCampaignsReported, emptySet())!!.toSet() }
        val first = intentOrReferral.campaignId !in campaignsReported
        // store as reported already, even if later call fails
        prefs.editPrefs {
            val newSet = campaignsReported.toMutableSet().apply { add(intentOrReferral.campaignId) }
            putStringSet(KeyCampaignsReported, newSet)
        }
        return resolveCampaign(intentOrReferral.original.toString(), first)
            .recoverCatching {
                logger.error("Failed to resolve campaign link: ${it.message}", it)
                resolveFinalDeeplink(intentOrReferral, null).getOrThrow()
            }
    }

    private fun resolveFinalDeeplink(originLinkKind: LinkKind?, deeplinkResponse: HeuristicsResult?): Result<Uri> {
        return runCatching {
            val deepLink = when (originLinkKind) {
                is LinkKind.CampaignLink -> originLinkKind.deeplink
                is LinkKind.RegularDeeplink -> originLinkKind.deeplink
                is LinkKind.Unknown -> {
                    logger.debug("Won't resolve unknown link kind: ${originLinkKind.original}")
                    return@runCatching originLinkKind.original.toString().toUri()
                }
                null -> null
            }

            deepLink?.toString()?.toUri()
                ?: deeplinkResponse?.deepLinkId?.toUri()
                ?: throw Exception("No valid deep link found to resolve")
        }
    }

    private suspend fun resolveCampaign(campaignUrl: String, firstOpen: Boolean): Result<Uri> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = httpClient.get("v1_get_campaign") {
                    url {
                        parameter("link", campaignUrl)
                        parameter("first_campaign_open", firstOpen.toString())
                    }
                }

                if (response.status.isSuccess()) {
                    response.body<FollowLinkResponse>().link.toUri()
                } else {
                    val errorResult = response.body<ErrorResult>()
                    logger.error("Failed to resolve campaign link, status: ${response.status} (${errorResult.error})")
                    throw Exception("Failed to resolve campaign link, status: ${response.status} - ${errorResult.error}")
                }
            }
        }
    }

    private suspend fun resolvePostInstallHeuristics(
        updatedAt: Instant,
        clipboardContentProvider: ClipboardContentProvider,
        intentLink: String?
    ): Result<HeuristicsResult> {


        return heuristicsInfoProvider.loadHeuristics(updatedAt,clipboardContentProvider, intentLink)
            .mapCatching { requestPayload ->
                val response = httpClient.post {
                    url("v1_postinstall_search_link")
                    contentType(ContentType.Application.Json)
                    setBody(requestPayload)
                }

                if (response.status.isSuccess()) {
                    val responseData = response.body<DeeplinkResponse>()
                    logger.debug("Successfully resolved link with heuristics: {}", responseData)
                    HeuristicsResult(
                        matchType = InternalMatchType.fromNetwork(responseData.matchType),
                        deepLinkId = responseData.deepLinkId
                            ?: throw Exception("No deep link ID found in the response"),
                        utmMedium = responseData.utmMedium,
                        utmSource = responseData.utmSource,
                        campaignId = responseData.campaignId,
                        clipboardUsed = requestPayload.uniqueMatchLinkToCheck != null
                    )
                } else {
                    logger.error("Failed to resolve link with heuristics, status: ${response.status}")
                    throw Exception("Failed to resolve link with heuristics, status: ${response.status}")
                }
            }

    }

    private suspend fun getInstallReferrer(updatedAt: Instant): Result<Uri> {
        val prefs = openPrefs()
        return if (updatedAt.isBefore(Instant.now(clock).minus(Duration.ofMinutes(30))) || prefs.readPrefs { getBoolean(KeyTracebackReferralQueried, false) }) {
            logger.info("App was updated more than 30 minutes ago, won't try to get install referrer")
            Result.failure(IllegalArgumentException("Install referrer not requested"))
        } else {
            installReferrerProvider.resolveInstallReferrer().also {
                prefs.editPrefs { putBoolean(KeyTracebackReferralQueried, true) }
            }
        }
    }
}

internal interface PreferencesProvider {
    suspend fun openPrefs(): SharedPreferences
}

internal interface InstallReferrerProvider {
    suspend fun resolveInstallReferrer(): Result<Uri>
}

internal interface HeuristicsInfoProvider {
    suspend fun loadHeuristics(
        updatedAt: Instant,
        clipboardContentProvider: ClipboardContentProvider,
        intentLink: String?
    ): Result<DeviceFingerprint>
}
class MissingMetadataException(message: String, cause: Throwable) : Exception(message, cause)
