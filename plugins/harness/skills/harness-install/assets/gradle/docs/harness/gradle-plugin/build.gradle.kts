plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        create("harnessValidation") {
            id = "ai.harness.validation"
            implementationClass = "ai.harness.gradle.HarnessValidationPlugin"
        }
    }
}
