plugins {
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform(libs.ktlint.bom))
    compileOnly(libs.ktlint.rule.engine.core)
    compileOnly(libs.ktlint.cli.ruleset.core)
    testImplementation(platform(libs.kotlin.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(platform(libs.ktlint.bom))
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.test)
    testRuntimeOnly(platform(libs.slf4j.bom))
    testRuntimeOnly(libs.slf4j.simple)
}

tasks.test {
    useJUnitPlatform()
}
