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
        // NewPipeExtractor is published only on JitPack; nothing else may resolve from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") { name = "JitPack" } }
            filter { includeGroup("com.github.TeamNewPipe") }
        }
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
    ":source:scrobble",
    ":source:youtube",
    ":feature:home",
    ":feature:search",
    ":feature:library",
    ":feature:player",
    ":feature:settings",
)
