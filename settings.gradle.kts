pluginManagement {
    repositories {
        // Mirrors first to avoid TLS issues with Fabric Maven
        maven("https://maven.quiltmc.org/repository/release/") {
            name = "QuiltMC"
        }
        maven("https://repo.hypixel.net/repository/maven-public/") {
            name = "HypixelMirror"
        }
        // Primary Fabric repository (kept, but placed after mirrors)
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        // Also allow Gradle Plugin Portal and Maven Central for plugins
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Apply repository configuration for all projects (dependencies resolution stage)
dependencyResolutionManagement {
    repositories {
        // Mirrors first
        maven("https://maven.quiltmc.org/repository/release/") {
            name = "QuiltMC"
        }
        maven("https://repo.hypixel.net/repository/maven-public/") {
            name = "HypixelMirror"
        }
        // Primary repositories
        mavenCentral()
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        // Additional repos used by the project can still be declared in module build scripts
    }
}
