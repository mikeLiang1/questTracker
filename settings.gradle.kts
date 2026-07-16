pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "QuestTracker"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":app", ":data", ":health", ":core")
