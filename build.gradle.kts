plugins {
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "com.lulybi"

    // 2. For Versions: Use the version-safe getter
    // This avoids the 'Extension not found' error by checking if it's ready
    val catalog = extensions.findByType<VersionCatalogsExtension>()?.named("libs")

    // Fallback to a hardcoded string if the catalog isn't ready during bootstrap
    version = catalog?.findVersion("lulybi")?.get()?.toString() ?: "1.0.0"
}

tasks.register<Delete>("clean") {
    description = "Ensure clean task wipes out the build directory for the entire monorepo"
    delete(rootProject.layout.buildDirectory)
}

// Configuration for Spotless across the entire workspace
subprojects {
    apply {
        plugin("com.diffplug.spotless")
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
            removeUnusedImports()
            trimTrailingWhitespace()
            target("src/*/java/**/*.java")
        }
    }
}