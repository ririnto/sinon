plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
    }
}

val kotlinCompilerLibrary = libs.kotlin.compiler.embeddable.get()
val kotlinCompilerModule: String = kotlinCompilerLibrary.module.toString()
val kotlinCompilerVersion: String = kotlinCompilerLibrary.versionConstraint.requiredVersion

val generateBuildConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildConfig/kotlin")
    outputs.dir(outDir)
    inputs.property("kotlinCompilerModule", kotlinCompilerModule)
    inputs.property("kotlinCompilerVersion", kotlinCompilerVersion)
    doLast {
        outDir.get().file("com/ririnto/sinon/harness/plugin/BuildConfig.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.ririnto.sinon.harness.plugin

                internal object BuildConfig {
                    const val KOTLIN_COMPILER_MODULE: String = "$kotlinCompilerModule"
                    const val KOTLIN_COMPILER_VERSION: String = "$kotlinCompilerVersion"
                }
                """.trimIndent() + "\n"
            )
        }
    }
}

kotlin.sourceSets["main"].kotlin.srcDir(generateBuildConfig)

dependencies {
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.commonmark.parent))
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.yaml.front.matter)
    implementation(libs.javaparser.core)
    compileOnly(libs.kotlin.compiler.embeddable)
}

gradlePlugin {
    plugins {
        create("harnessCheck") {
            id = "com.ririnto.sinon.harness"
            implementationClass = "com.ririnto.sinon.harness.plugin.HarnessValidationPlugin"
        }
    }
}
