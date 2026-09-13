# Gradle Java tooling

This directory contains native Gradle Java integration sources.

Merge `build.gradle.kts.fragment` into the target's existing build and copy the canonical `../java/checkstyle.xml` to `tooling/java/checkstyle.xml`.
The active Kotlin ruleset remains at `../kotlin-ktlint/` and is owned by the Kotlin integration.
Copy that complete module into the target-owned tooling tree, build its JAR through the native Gradle task graph, and add the JAR with `ktlintRuleset(...)` so the ktlint runtime loads its `RuleSetProviderV3` service descriptor.
