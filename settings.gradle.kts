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

rootProject.name = "sitara"

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:data",
    ":core:player",
    ":core:designsystem",
    ":source:api",
    ":source:fake",
    ":source:lyrics",
    ":feature:home",
    ":feature:search",
    ":feature:library",
    ":feature:player",
    ":feature:settings",
)
