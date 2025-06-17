import com.android.build.api.dsl.CommonExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

private fun CommonExtension<*, *, *, *, *, *>.setupCommonValues() {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        targetSdk = 36
    }
}

val catalog = the(VersionCatalogsExtension::class).named("libs")
subprojects {

    pluginManager.withPlugin("com.android.base") {
        extensions.configure<CommonExtension<*, *, *, *, *, *>>("android") {
            setupCommonValues()
        }

        dependencies {
            add("coreLibraryDesugaring", catalog.findLibrary("coreLibraryDesugaring").get())
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.android") {
            extensions.configure<KotlinAndroidProjectExtension>("kotlin") {
                compilerOptions {
                    apiVersion.set(KotlinVersion.KOTLIN_2_1)
                    languageVersion.set(KotlinVersion.KOTLIN_2_1)
                    jvmTarget.set(JvmTarget.JVM_17)
                    freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
                }
            }
        }
    }

}
