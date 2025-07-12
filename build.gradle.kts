plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Remove this for local publishing: alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "io.flowkit"
    version = "0.1.0-SNAPSHOT" // Use SNAPSHOT for local development

    repositories {
        google()
        mavenCentral()
        mavenLocal() // Important for local publishing
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Task to publish all modules to local Maven
tasks.register("publishAllToMavenLocal") {
    dependsOn(
        ":flowkit-core:publishToMavenLocal",
        ":flowkit-network:publishToMavenLocal",
        ":flowkit-storage:publishToMavenLocal"
    )
    group = "publishing"
    description = "Publish all FlowKit modules to Maven Local"
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