plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

dependencies {
    implementation(project(":library"))
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set("example.Main")
}

tasks.test {
    useJUnitPlatform()
}
