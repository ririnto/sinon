plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform("com.pinterest.ktlint:ktlint-bom:1.8.0"))
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core")
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core")
    testImplementation(platform("com.pinterest.ktlint:ktlint-bom:1.8.0"))
    testImplementation("com.pinterest.ktlint:ktlint-cli-ruleset-core")
    testImplementation("com.pinterest.ktlint:ktlint-rule-engine")
    testImplementation("com.pinterest.ktlint:ktlint-rule-engine-core")
    testImplementation(platform("io.kotest:kotest-bom:6.2.6"))
    testImplementation("io.kotest:kotest-runner-junit5-jvm")
    testImplementation("io.kotest:kotest-assertions-core-jvm")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    version.set("1.8.0")
    android.set(false)
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)
}

tasks.test {
    useJUnitPlatform()
}
