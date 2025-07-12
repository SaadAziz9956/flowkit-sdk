import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

kotlin {
    // Android target
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    // iOS targets - Full iOS support
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FlowKitCompose"
            isStatic = true
        }
    }

    // Desktop support (optional)
    jvm("desktop")

    sourceSets {
        // Common dependencies - Only core multiplatform libraries
        commonMain.dependencies {
            // FlowKit core modules
            api(project(":flowkit-core"))
            api(project(":flowkit-network"))
            api(project(":flowkit-storage"))

            // Core Compose Multiplatform dependencies
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.animation)

            // Coroutines - multiplatform
            api(libs.kotlinx.coroutines.core)

            // DateTime for time-based operations
            implementation(libs.kotlinx.datetime)
        }

        // Android-specific dependencies
        androidMain.dependencies {
            // Android-specific coroutines
            implementation(libs.kotlinx.coroutines.android)

            // Android Lifecycle (only for Android)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.ktx)
            implementation(libs.androidx.lifecycle.runtime.compose)

            // Android-specific Compose and Navigation
            implementation(libs.activity.compose)
            implementation(libs.androidx.navigation.compose)
        }

        // iOS-specific dependencies
        iosMain.dependencies {
            // iOS-specific dependencies if needed
            // Keep this minimal - most functionality is in commonMain
        }

        // Desktop-specific dependencies
        val desktopMain by getting {
            dependencies {
                // Desktop-specific Compose dependencies
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.ui.tooling.preview)
            }
        }

        // Common test dependencies
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions.core)
        }

        // Android test dependencies
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Android instrumented test dependencies
        androidInstrumentedTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
        }
    }
}

android {
    namespace = "io.flowkit.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// Publishing configuration
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "io.flowkit",
        artifactId = "flowkit-compose",
        version = project.version.toString()
    )

    pom {
        name.set("FlowKit Compose")
        description.set("Compose Multiplatform UI utilities and reactive integrations for FlowKit SDK")
        url.set("https://github.com/SaadAziz9956/flowkit-sdk")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("flowkit-team")
                name.set("FlowKit Team")
                email.set("team@flowkit.io")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/SaadAziz9956/flowkit-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/SaadAziz9956/flowkit-sdk.git")
            url.set("https://github.com/SaadAziz9956/flowkit-sdk")
        }
    }
}