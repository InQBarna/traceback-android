import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

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

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.dokka)
    `maven-publish`
    signing
}

dokka {
    moduleName.set("Android Traceback SDK")
    dokkaSourceSets.main {
        sourceLink {
            localDirectory.set(file("src/main/java"))
            remoteUrl.set(uri("http://localhost/"))
        }
        documentedVisibilities(VisibilityModifier.Public)
    }
}

android {
    namespace = "com.inqbarna.traceback.sdk"

    defaultConfig {
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.slf4jApi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.reflect)
    implementation(libs.google.installreferrer)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.content.negotiation)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.logback)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.androidx.espresso.core)
}

if (hasProperty("ossUser")) {
    val libraryVersion = "1.0.0"
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.inqbarna"
                artifactId = "traceback-sdk"
                version = libraryVersion

                pom {
                    name = "Android Traceback SDK"
                    description = "Traceback SDK provides functionality that before was achieved with Firebase Dynamic Links"
                    url = "https://github.com/InQBarna/traceback-android"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://github.com/InQBarna/traceback-android/blob/main/LICENSE"
                        }
                    }

                    developers {
                        developer {
                            name = "David García"
                            id = "davidgarcia"
                            email = "david.garcia@inqbarna.com"
                            organization = "Inqbarna Kenkyuu Jo"
                        }
                    }

                    scm {
                        url = "https://github.com/InQBarna/traceback-android/"
                    }
                }

                afterEvaluate {
                    from(components["release"])
                }
            }
        }

        repositories {
            maven {
               name = "OSSS01"
                val snapshotUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                val stagingUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                url = if (libraryVersion.endsWith("SNAPSHOT")) snapshotUrl else stagingUrl
                credentials {
                    username = project.property("ossUser") as String
                    password = project.property("ossToken") as String
                }
            }
        }
    }

    signing {
        useGpgCmd()
        sign(publishing.publications.named("release").get())
    }
}
