pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "karoo-partnereta"

include(":app")

// Hammerhead's karoo-ext library (v1.1.9, Apache 2.0), built from source so no
// GitHub Packages token is needed. See karoo-ext/LICENSE.
include(":karoo-ext")

// karoo-common: the code every DDB app shares (setup/sync-kit.sh; never edit it here).
include(":karoo-common")
