#!/usr/bin/env python3
"""Generate and validate Java and Kotlin API docs with the shipped fragments."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET


fixture = Path(__file__).resolve().parent
harness = fixture.parents[2]
gradle = os.environ.get("GRADLE", "gradle")
maven = os.environ.get("MAVEN", "mvn")

with tempfile.TemporaryDirectory(prefix="sinon-jvm-documentation-") as temporary:
    project = Path(temporary)
    shutil.copytree(fixture / "src", project / "src")
    shutil.copy(fixture / "settings.gradle.kts", project)
    build = (fixture / "build.gradle.kts").read_text()
    for name in ("javadoc.gradle.kts.fragment", "dokka.gradle.kts.fragment"):
        build += "\n" + (harness / "tooling/gradle" / name).read_text()
    (project / "build.gradle.kts").write_text(build)
    fragment = ET.parse(harness / "tooling/maven/pom.xml.fragment").getroot()
    plugin = next(
        plugin for plugin in fragment.find("plugins")
        if plugin.findtext("artifactId") == "maven-javadoc-plugin"
    )
    version = fragment.findtext("properties/maven.javadoc.version")
    pom = (fixture / "pom.xml").read_text()
    pom = pom.replace("</properties>", f"<maven.javadoc.version>{version}</maven.javadoc.version></properties>")
    pom = pom.replace("<plugins />", f"<plugins>{ET.tostring(plugin, encoding='unicode')}</plugins>")
    (project / "pom.xml").write_text(pom)
    kotlin_fragment = ET.parse(harness / "tooling/maven/dokka.pom.xml.fragment").getroot()
    kotlin_plugin = kotlin_fragment.find("plugins/plugin")
    kotlin_version = kotlin_fragment.findtext("properties/dokka.version")
    kotlin_pom = (fixture / "pom.xml").read_text()
    kotlin_pom = kotlin_pom.replace("</properties>", f"<dokka.version>{kotlin_version}</dokka.version></properties>")
    kotlin_pom = kotlin_pom.replace("<build>", "<build><sourceDirectory>src/main/kotlin</sourceDirectory>")
    kotlin_pom = kotlin_pom.replace("<plugins />", f"<plugins>{ET.tostring(kotlin_plugin, encoding='unicode')}</plugins>")
    (project / "kotlin-pom.xml").write_text(kotlin_pom)

    def run(command, expected_success):
        result = subprocess.run(command, cwd=project, text=True, capture_output=True, check=False)
        output = result.stdout + result.stderr
        print(f"{' '.join(command)}: exit {result.returncode}")
        if (result.returncode == 0) != expected_success:
            print(output)
            raise SystemExit("Unexpected documentation generation result")
        return output

    source = project / "src/main/java/example/JavaGreeting.java"
    valid = source.read_text()
    source.write_text(valid.replace("@param name", "@param missing").replace("Formats a greeting for the supplied recipient.", "Formats a greeting with {@link MissingType}."))
    for command in ([gradle, "--no-daemon", "javadoc"], [maven, "javadoc:javadoc"]):
        output = run(command, False)
        if "@param" not in output or "MissingType" not in output:
            print(output)
            raise SystemExit("Expected DocLint parameter and reference errors")
    source.write_text(valid)
    for command in ([gradle, "--no-daemon", "javadoc", "dokkaGenerate"], [maven, "javadoc:javadoc"], [maven, "-f", "kotlin-pom.xml", "dokka:dokka"]):
        output = run(command, True)
        if "warning:" in output.lower():
            print(output)
            raise SystemExit("Unexpected documentation warning")
    for output in (
        project / "build/docs/javadoc/example/JavaGreeting.html",
        project / "build/dokka/html/index.html",
        project / "target/reports/apidocs/example/JavaGreeting.html",
        project / "target/dokka/index.html",
    ):
        if not output.is_file():
            raise SystemExit(f"Missing generated output: {output}")
    if "formatted greeting" not in (project / "build/docs/javadoc/example/JavaGreeting.html").read_text():
        raise SystemExit("Java API contract is missing from generated documentation")
    if not any("KotlinGreeting" in file.read_text() for file in (project / "build/dokka/html").rglob("*.html")):
        raise SystemExit("Kotlin API is missing from generated documentation")
    if not any("KotlinGreeting" in file.read_text() for file in (project / "target/dokka").rglob("*.html")):
        raise SystemExit("Kotlin API is missing from Maven-generated documentation")
    print("Java and Kotlin API documentation generated without warnings.")
    source.write_text(valid.replace("public final class JavaGreeting {", "public final class JavaGreeting {\n    public int count;"))
    output = run([gradle, "--no-daemon", "javadoc"], True)
    if "warning: no comment" not in output:
        print(output)
        raise SystemExit("Expected a nonfatal warning for the undocumented field")
    print("DocLint reports an undocumented public field without failing generation.")
