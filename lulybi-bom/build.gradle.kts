plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        // We go to the source: the project's own dependency helper
        // This is safer than the 'VersionCatalogsExtension' in restricted plugins
        val catalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

        // Manually adding the core modules by their names in the TOML
        // 'findLibrary' returns an Optional/Provider that is safe to use here
        api(catalog.findLibrary("lulybi-core").get())
        api(catalog.findLibrary("lulybi-athena").get())
    }
}