pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.git.hooks)
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "harness-target"

gitHooks {
    preCommit {
        tasks("ktlintCheck")
    }
    hook("pre-push") {
        tasks("check")
    }
    createHooks()
}
