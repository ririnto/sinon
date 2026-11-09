plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
}

tasks.named("check") {
    dependsOn(gradle.includedBuild("kotlin").task(":check"), ":app:check", ":library:check")
}

tasks.register("ktlintCheck") {
    dependsOn(":app:ktlintCheck", ":library:ktlintCheck")
}
