plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(platform("com.pinterest.ktlint:ktlint-bom:1.8.0"))
    compileOnly("com.pinterest.ktlint:ktlint-rule-engine-core")
    compileOnly("com.pinterest.ktlint:ktlint-cli-ruleset-core")
}
