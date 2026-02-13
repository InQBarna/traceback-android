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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.dokka) apply false
    alias(libs.plugins.nexus.publish)
}

group = "com.inqbarna"


private val CompileSdkVersion = 36


if (hasProperty("ossUser")) {
    nexusPublishing {
        repositories {
            // see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
            sonatype {
                nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
                snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
                username = project.property("ossUser") as String
                password = project.property("ossToken") as String
            }
        }
    }
}

val catalog = the(VersionCatalogsExtension::class).named("libs")
subprojects {

    pluginManager.withPlugin("com.android.base") {
        extensions.configure<CommonExtension>("android") {
            if (this is LibraryExtension) {
                defaultConfig {
                    aarMetadata {
                        minCompileSdk = CompileSdkVersion
                    }
                }
            }

            when (this) {
                is ApplicationExtension -> {
                    defaultConfig {
                        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
                is LibraryExtension -> {
                    defaultConfig {
                        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                        aarMetadata {
                            minCompileSdk = CompileSdkVersion
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }


                }
            }
        }

        dependencies {
            add("coreLibraryDesugaring", catalog.findLibrary("coreLibraryDesugaring").get())
        }

        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                apiVersion.set(KotlinVersion.KOTLIN_2_3)
                languageVersion.set(KotlinVersion.KOTLIN_2_3)
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-Xcontext-parameters",
                    "-Xannotation-default-target=param-property",
                    "-Xwhen-guards",
                    "-Xexplicit-backing-fields"
                )
            }
        }
    }

}
