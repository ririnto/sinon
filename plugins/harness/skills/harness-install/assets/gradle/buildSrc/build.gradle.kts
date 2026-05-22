plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
}

val kotlinCompiler: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    kotlinCompiler(libs.kotlin.compiler.embeddable)
}

gradlePlugin {
    plugins {
        create("harnessValidation") {
            id = "ai.harness.validation"
            implementationClass = "ai.harness.gradle.HarnessValidationPlugin"
        }
    }
}
