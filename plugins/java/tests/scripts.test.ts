import { spawn, spawnSync } from "node:child_process";
import { once } from "node:events";
import { mkdir, mkdtemp, realpath, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { describe, expect, onTestFinished, test } from "vitest";

const scripts = path.resolve(import.meta.dirname, "../scripts");

const fixture = async () => {
  const directory = await realpath(
    await mkdtemp(path.join(tmpdir(), "sinon-java-scripts-"))
  );
  onTestFinished(() => rm(directory, { force: true, recursive: true }));
  const bin = path.join(directory, "bin");
  const project = path.join(directory, "project");
  await Promise.all([mkdir(bin), mkdir(project)]);
  const excluded = new Set([
    "JAVA_ASSISTANT_LOMBOK_JAR",
    "JAVA_ASSISTANT_LOMBOK_ENABLED",
    "JAVA_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED",
    "JDK_JAVA_OPTIONS"
  ]);
  const environment: NodeJS.ProcessEnv = Object.fromEntries(
    Object.entries(process.env).filter(([key]) => !excluded.has(key))
  );
  environment.PATH = `${bin}:/usr/bin:/bin`;
  await writeFile(path.join(project, "pom.xml"), "<project/>\n");
  await writeFile(
    path.join(bin, "jdtls"),
    `#!${process.execPath}\nprocess.stdout.write(JSON.stringify({ args: process.argv.slice(2), options: process.env.JDK_JAVA_OPTIONS ?? null, pid: process.pid }));\n`,
    { mode: 0o755 }
  );
  return { bin, directory, environment, project };
};

describe("Java shell script behavior", () => {
  test.each([
    ["pom.xml", "<artifactId>lombok</artifactId>", 0],
    ["build.gradle.kts", 'compileOnly("org.projectlombok:lombok")', 0],
    ["gradle/libs.versions.toml", 'lombok = "1.18.40"', 0],
    ["module/pom.xml", "org.projectlombok", 0],
    ["build/pom.xml", "org.projectlombok", 1],
    ["pom.xml", "<project/>", 1]
  ])("Lombok detection in %s", async (file, content, status) => {
    const { project } = await fixture();
    const target = path.join(project, file);
    await mkdir(path.dirname(target), { recursive: true });
    await writeFile(target, content);
    const result = spawnSync("sh", [
      path.join(scripts, "has-lombok.sh"),
      project
    ]);
    expect(result.status, result.stderr.toString()).toBe(status);
  });

  test.each([
    ["1.18.39", 1],
    ["1.18.40", 0],
    ["1.18.40.0", 0],
    ["1.18.40-RC_a.1", 0],
    ["1.18.40+build", 1],
    ["1.18.42", 0],
    ["2.0.0", 0]
  ])("project jar version %s", async (version, status) => {
    const { project } = await fixture();
    const jar = path.join(project, `lombok-${version}.jar`);
    await writeFile(jar, "fixture");
    await writeFile(
      path.join(project, ".classpath"),
      `path="${path.basename(jar)}"`
    );
    const result = spawnSync("sh", [
      path.join(scripts, "has-lombok.sh"),
      "--resolve-project-jar",
      project
    ]);
    expect(result.status, result.stderr.toString()).toBe(status);
    expect(result.stdout.toString()).toBe(status === 0 ? `${jar}\n` : "");
  });

  test("jar search retains metadata precedence and first compatible candidate", async () => {
    const { project } = await fixture();
    const first = path.join(project, "lombok-1.18.40.jar");
    const second = path.join(project, "lombok-1.18.42.jar");
    await Promise.all([
      writeFile(first, "fixture"),
      writeFile(second, "fixture")
    ]);
    await writeFile(
      path.join(project, ".classpath"),
      'path="missing/lombok-1.18.39.jar"\npath="lombok-1.18.40.jar"'
    );
    await writeFile(
      path.join(project, ".factorypath"),
      'path="lombok-1.18.42.jar"'
    );
    const result = spawnSync("sh", [
      path.join(scripts, "has-lombok.sh"),
      "--resolve-project-jar",
      project
    ]);
    expect(result.status).toBe(0);
    expect(result.stdout.toString()).toBe(`${first}\n`);
  });

  test.each([
    "default",
    "disabled",
    "override",
    "missing-override",
    "unsafe-override",
    "project",
    "override-precedence",
    "fallback"
  ])("launcher %s preserves arguments and selection", async (mode) => {
    const { directory, environment, project } = await fixture();
    const override = path.join(directory, "override.jar");
    const projectJar = path.join(project, "lombok-1.18.40.jar");
    await Promise.all([
      writeFile(override, "fixture"),
      writeFile(projectJar, "fixture")
    ]);
    await writeFile(
      path.join(project, ".classpath"),
      'path="lombok-1.18.40.jar"'
    );
    environment.JDK_JAVA_OPTIONS =
      "-Xmx1g -javaagent:old-lombok.jar -Dkeep=yes";
    if (mode === "disabled") {
      environment.JAVA_ASSISTANT_LOMBOK_ENABLED = "false";
      environment.JAVA_ASSISTANT_LOMBOK_JAR = override;
    } else if (mode === "override" || mode === "override-precedence") {
      environment.JAVA_ASSISTANT_LOMBOK_JAR = override;
    } else if (mode === "missing-override" || mode === "fallback") {
      environment.JAVA_ASSISTANT_LOMBOK_JAR = path.join(
        directory,
        "missing.jar"
      );
    } else if (mode === "unsafe-override") {
      environment.JAVA_ASSISTANT_LOMBOK_JAR = path.join(
        directory,
        "unsafe path.jar"
      );
    }
    if (["project", "override-precedence", "fallback"].includes(mode)) {
      environment.JAVA_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED = "true";
    }
    const nested = path.join(project, "src/nested");
    await mkdir(nested, { recursive: true });
    const args = ["--data", "value with spaces", "*literal*"];
    const result = spawnSync(
      "sh",
      [path.join(scripts, "jdtls-wrapper.sh"), ...args],
      {
        cwd: nested,
        env: environment
      }
    );
    expect(result.status, result.stderr.toString()).toBe(0);
    const output = JSON.parse(result.stdout.toString());
    expect(output.args).toEqual(args);
    let selected: string | undefined;
    if (["override", "override-precedence"].includes(mode)) {
      selected = override;
    } else if (["project", "fallback"].includes(mode)) {
      selected = projectJar;
    }
    expect(output.options).toBe(
      selected
        ? `-javaagent:${selected} -Xmx1g -Dkeep=yes`
        : environment.JDK_JAVA_OPTIONS
    );
    const warnings: string[] = [];
    if (mode === "missing-override" || mode === "fallback") {
      warnings.push(
        `java: Lombok override jar was not found at ${environment.JAVA_ASSISTANT_LOMBOK_JAR}. Ignoring override.`
      );
    } else if (mode === "unsafe-override") {
      warnings.push(
        "java: Lombok jar path must be a single filesystem token without whitespace. Ignoring override."
      );
    }
    if (selected) {
      const source = ["override", "override-precedence"].includes(mode)
        ? "override"
        : "project";
      warnings.push(
        `java: Enabled Lombok support from ${source} source (${selected}).`
      );
    }
    expect(result.stderr.toString()).toBe(
      warnings.length > 0 ? `${warnings.join("\n")}\n` : ""
    );
  });

  test("missing launcher returns its existing status", async () => {
    const { bin, environment, project } = await fixture();
    await rm(path.join(bin, "jdtls"));
    const result = spawnSync("sh", [path.join(scripts, "jdtls-wrapper.sh")], {
      cwd: project,
      env: environment
    });
    expect(result.status).toBe(127);
    expect(result.stderr.toString()).toBe(
      "java: jdtls executable not found on PATH. Install jdtls first.\n"
    );
  });

  test("exec preserves the launcher PID and termination contract", async () => {
    const { bin, environment, project } = await fixture();
    await writeFile(
      path.join(bin, "jdtls"),
      `#!${process.execPath}\nprocess.on('SIGTERM', () => process.exit(143));\nprocess.stdout.write(String(process.pid));\nsetInterval(() => {}, 1000);\n`,
      { mode: 0o755 }
    );
    const child = spawn("sh", [path.join(scripts, "jdtls-wrapper.sh")], {
      cwd: project,
      env: environment
    });
    onTestFinished(() => {
      if (child.exitCode === null && child.signalCode === null) {
        child.kill("SIGKILL");
      }
    });
    const closed = once(child, "close");
    const [ready] = await once(child.stdout, "data");
    expect(Number(ready.toString())).toBe(child.pid);
    child.kill("SIGTERM");
    expect(await closed).toEqual([143, null]);
  });
});
