plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

gradlePlugin {
    plugins {
        register("harnessConvention") {
            id = "local.harness-convention"
            implementationClass = "HarnessConventionPlugin"
        }
    }
}
