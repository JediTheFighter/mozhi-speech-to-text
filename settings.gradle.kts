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

rootProject.name = "mozhi-speech-to-text"

include(
    ":app",
    ":domain",
    ":data",
    ":core:common",
    ":core:designsystem",
    ":core:audio",
    ":core:stt",
    ":core:translation",
    ":feature:transcribe",
    ":feature:models",
)
