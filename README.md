# Traceback SDK for Android

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

Traceback link will be of the form `https://your-traceback-domain.com/traceback?link=<your-link>`. This method will return the `link` at `link` parameter if any
or resolve it through the backend if not present.

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
