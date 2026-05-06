rootProject.name = "lulybi"

// Enable the Version Catalog
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// Include the Hub, the AOT Engine, and the Service Spokes
include("lulybi-core")
include("lulybi-athena")
include("lulybi-bom")