pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.0.9"
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
