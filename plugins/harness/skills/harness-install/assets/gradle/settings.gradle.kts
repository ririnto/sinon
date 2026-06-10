pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.9"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "project"

gitHooks {
    preCommit {
        tasks("ktlintCheck")
    }
    hook("pre-push") {
        tasks("check")
    }
    createHooks()
}
