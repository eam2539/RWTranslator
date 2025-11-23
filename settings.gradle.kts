pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://mirrors.cloud.tencent.com/nexus/content/repositories/google")
    }
}

dependencyResolutionManagement {

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://mirrors.cloud.tencent.com/nexus/content/repositories/google")
    }
}
rootProject.name = "RWTranslator"

include(":app")