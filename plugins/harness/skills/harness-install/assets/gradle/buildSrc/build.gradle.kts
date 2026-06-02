plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    embeddedKotlin("plugin.serialization")
}

val ktlintRuleEngineModule: String = "com.pinterest.ktlint:ktlint-rule-engine"
val ktlintRuleEngineCoreModule: String = "com.pinterest.ktlint:ktlint-rule-engine-core"
val ktlintVersion: String = "1.8.0"

val generateBuildConfig by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/buildConfig/kotlin")
    outputs.dir(outDir)
    inputs.property("ktlintRuleEngineModule", ktlintRuleEngineModule)
    inputs.property("ktlintRuleEngineCoreModule", ktlintRuleEngineCoreModule)
    inputs.property("ktlintVersion", ktlintVersion)
    doLast {
        outDir.get().file("com/ririnto/sinon/harness/plugin/BuildConfig.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package com.ririnto.sinon.harness.plugin

                internal object BuildConfig {
                    const val KTLINT_RULE_ENGINE_MODULE: String = "$ktlintRuleEngineModule"
                    const val KTLINT_RULE_ENGINE_CORE_MODULE: String = "$ktlintRuleEngineCoreModule"
                    const val KTLINT_VERSION: String = "$ktlintVersion"
                }
                """.trimIndent()
                    + "\n",
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
    compileOnly(libs.ktlint.rule.engine)
    compileOnly(libs.ktlint.rule.engine.core)
    testImplementation(libs.ktlint.rule.engine)
    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("harnessCheck") {
            id = "com.ririnto.sinon.harness"
            implementationClass = "com.ririnto.sinon.harness.plugin.HarnessValidationPlugin"
        }
    }
}
