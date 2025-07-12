enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal() // Important for local publishing
    }
}

rootProject.name = "flowkit-sdk"
include(":flowkit-core")
include(":flowkit-network")
include(":flowkit-storage")
include(":samples")
include(":samples:todo-app-mvi")