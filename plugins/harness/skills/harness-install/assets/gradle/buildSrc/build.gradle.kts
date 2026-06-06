plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform("com.pinterest.ktlint:ktlint-bom:${libs.versions.ktlint.cli.get()}"))
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core")
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core")
}
