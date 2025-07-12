import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }


    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                    freeCompilerArgs.add("-Xcontext-receivers")
                }
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FlowKitCodegen"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flowkit-core"))
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.serialization.json)
        }

        // JVM-specific code generation logic
        jvmMain.dependencies {
            implementation(libs.bundles.ksp.processing)
        }

        commonTest.dependencies {
            implementation(libs.bundles.testing)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            compileOnly(libs.androidx.lifecycle.viewmodel.ktx)
        }
    }
}

// Android configuration
android {
    namespace = "io.flowkit.codegen"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Publishing configuration
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "io.flowkit",
        artifactId = "flowkit-codegen",
        version = project.version.toString()
    )

    pom {
        name.set("FlowKit CodeGen")
        description.set("Cross-platform code generation for MVI patterns using KSP")
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