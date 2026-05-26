# Maven Harness Validator

Run `mvn -q -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check`.

## Structural parity

The Maven validator mirrors the Gradle harness model with `com.ririnto.sinon.harness.core` for `Manifest`, `RuleContext`, and `Severity`, an enum registry in `HarnessCheck`, and rule packages grouped under `rules/fs`, `rules/text`, and `rules/ast`. Rule implementations receive `RuleContext` through `HarnessCheckRule`, keeping manifest and filesystem access behind the shared context surface.
