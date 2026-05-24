plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
    }
}

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.javaparser.core)
    compileOnly(libs.kotlin.compiler.embeddable)
}

gradlePlugin {
    plugins {
        create("harnessValidation") {
            id = "ai.harness.validation"
            implementationClass = "ai.harness.gradle.HarnessValidationPlugin"
        }
    }
}
