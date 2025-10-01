# Traceback SDK for Android

![Maven Central Version](https://img.shields.io/maven-central/v/com.inqbarna/traceback-sdk)


Traceback SDK is designed to work seamlessly with the [Firebase Traceback Extension](https://github.com/InQBarna/firebase-traceback-extension), providing a robust alternative to Firebase Dynamic Links for deep linking and attribution in Android applications.

## Features

- **Deep Link Handling**: Receive and process deep links in your app.
- **Easy Initialization**: Automatic SDK initialization at app startup.

## Getting Started

### 1. Installation

Add the SDK dependency to your `build.gradle`:

```gradle
implementation 'com.inqbarna:traceback-sdk:<latest-version>'
```

### 2. Initialization

The SDK is initialized automatically via a `ContentProvider`. No manual setup is required.


### 3. Integration with Firebase Traceback Extension

Follow the [Firebase Traceback Extension setup guide](https://github.com/InQBarna/firebase-traceback-extension?tab=readme-ov-file#readme) to configure your backend for link generation and attribution.

### 4. Handling Deep Links

Call the method `resolvePendingTracebackLink` to start link resolution. This is intended to resolve traceback links and extract
link of interest for the application.

Traceback link will be of the form `https://your-traceback-domain.com/somecontent. This method will resolve it through the backend to the proper
deeplink you need to navigate too.

```kotlin
override fun onCreate(savedStateBundle: Bundle?) {
    
    Traceback.resolvePendingTracebackLink(intent)
        .onSuccess { link ->
            // Handle the resolved link, and navigate to the appropriate screen
        }
        .onFailure { 
            // error usually means that the link is not a traceback link you should proceed with the default intent handling
        }
}
```

In order to improve heuristics, traceback can use clipboard for the cases where application is not installed from the beginning and 
install referral mechanism doesn't work.

To facilitate clipboard, pass a `Flow<Boolean>` to the `resolvePendingTracebackLink` method. It will be used to try to await till
application window has gained focus and clipboard is available.

This could be implemented in a hosting activity like this:

```kotlin
class AppActivity : AppCompatActivity() {
    private var windowFocusCallback: CompletableDeferred<Boolean>? = null
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowFocusCallback?.complete(true)
            windowFocusCallback = null
        }
    }

    private fun createFocusGainSignal(): Flow<Boolean> = flow<Boolean> {
        if (hasWindowFocus()) {
            emit(true)
        } else {
            val callback = CompletableDeferred<Boolean>().also {
                windowFocusCallback = it
            }
            emit(callback.await())
        }
    }
}
```

### 5. Android Manifest Configuration

You need to declare your extension domain as an `AndroidManifest.xml` meta-data entry:

```xml
<application>
    <meta-data
        android:name="com.inqbarna.traceback.domain"
        android:value="your-traceback-domain.com" />
</application>
```

#### 5.1. Optional Configuration

By default, the Traceback SDK uses standard settings for link resolution and analytics. However, you can customize its behavior by providing your own configuration provider.

To do this, implement a class that implements `TracebackConfigProvider` and declare it in your `AndroidManifest.xml` as a meta-data entry:

```xml
<application>
    <!-- Required: Set your Traceback domain -->
    <meta-data
        android:name="com.inqbarna.traceback.domain"
        android:value="your-traceback-domain.com" />

    <!-- Optional: Provide a custom configuration provider -->
    <meta-data
        android:name="com.inqbarna.traceback.sdk.TracebackConfigProvider"
        android:value="com.yourpackage.YourTracebackConfigProvider" />
</application>
```

Your custom provider must expose a `configure` property which is a lambda with receiver on `TracebackConfigBuilder`. The SDK will instantiate your provider and invoke this lambda during initialization. For example:

```kotlin
class YourTracebackConfigProvider : TracebackConfigProvider {
    override val configure = {
        // Set the minimum match type the SDK should consider when resolving links
        minMatchType(MatchType.Ambiguous)

        // Provide a custom analytic client to receive resolution events
        setAnalyticClient(object : AnalyticClient {
            override fun onResolveSource(source: ResolveSource, parameters: ResolveParameters) {
                // Custom analytics logic
            }

            override fun onResolveFail(source: ResolveSource, parameters: ResolveParameters) {
                // Custom analytics logic for failures
            }
        })
    }
}
```

If no provider is specified, the SDK will use its default configuration. This mechanism allows you to adjust analytics, heuristics, or other advanced settings as needed for your app.
