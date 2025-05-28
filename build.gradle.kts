plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}


allprojects {
    group = "io.flowkit"
    version = "0.1.0-alpha"

    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

// Task to run all tests
tasks.register("testAll") {
    dependsOn(subprojects.map { "${it.path}:test" })
    group = "verification"
    description = "Run tests for all modules"
}

// Task to generate documentation
tasks.register("generateDocs") {
    dependsOn(subprojects.map { "${it.path}:dokkaHtml" })
    group = "documentation"
    description = "Generate documentation for all modules"
}