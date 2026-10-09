plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

group = "com.ririnto.sinon.harness"
version = "1.0.0"

dependencies {
    compileOnly(platform(libs.ktlint.bom))
    compileOnly(libs.ktlint.cli.ruleset.core)
    compileOnly(libs.ktlint.rule.engine.core)
    testImplementation(platform(libs.ktlint.bom))
    testImplementation(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(platform(libs.kotest.bom))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.slf4j.simple)
    ktlintRuleset(files(tasks.jar))
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    android.set(false)
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)
}

tasks.test {
    useJUnitPlatform()
}
