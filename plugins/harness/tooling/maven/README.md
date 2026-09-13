# Maven Java tooling

This directory contains native Maven Java integration sources.

Merge `pom.xml.fragment` into an existing POM and copy the canonical `../java/checkstyle.xml` to `tooling/java/checkstyle.xml`.
Preserve the target's coordinates, parent, lifecycle, source roots, repositories, and toolchain.
Kotlin Maven handling is conditional: use the separately built `../kotlin-ktlint/` JAR only when the target already exposes a verified KtLint CLI classpath and native build dependency.
