import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    `maven-publish`
}

kotlin {
    // Add JVM target for compatibility
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
        publishLibraryVariants("release", "debug")
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "flowkit-network"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flowkit-core"))
            api(libs.kotlinx.coroutines.core)

            // Optional Ktor integration
            compileOnly(libs.ktor.client.core)
            compileOnly(libs.ktor.client.content.negotiation)
            compileOnly(libs.ktor.serialization.kotlinx.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            compileOnly(libs.ktor.client.android)
        }

    }
}

android {
    namespace = "io.flowkit.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            groupId = "io.flowkit"
            version = "0.1.0-SNAPSHOT"

            pom {
                name.set("FlowKit Network")
                description.set("Network utilities and Flow integrations for FlowKit SDK")
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
    }
}