// Pengaturan proyek Chill World
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

rootProject.name = "ChillWorld"

// Versi Windows PC (LWJGL + OpenGL 4.3). Kode render dibagi dengan :app lewat folder sumber bersama.
include(":desktop")

// Build Windows pakai -PdesktopOnly=true supaya Android SDK tidak dibutuhkan
if (!providers.gradleProperty("desktopOnly").isPresent) {
    include(":app")
}
