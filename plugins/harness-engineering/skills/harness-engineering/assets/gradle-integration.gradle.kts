tasks.register<Exec>("harnessCheck") {
    group = "verification"
    description = "Validate harness-engineering repository configuration."
    commandLine("sh", "scripts/harness/validate_harness.sh")
}

tasks.named("check") {
    dependsOn("harnessCheck")
}
