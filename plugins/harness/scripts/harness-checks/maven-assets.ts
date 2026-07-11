// -*- coding: utf-8 -*-

import path from "node:path";

import { requiredJavaReleaseForCheckstyle } from "./asset-versions.js";
import {
  fail,
  readRequiredCapture,
  rejectTextFragments,
  requireFile,
  requireTexts
} from "./check-support.js";

const checkGithubWorkflow = (
  filePath: string,
  fragments: readonly string[]
): void => {
  requireFile(filePath);
  requireTexts([{ fragments, path: filePath }]);
};

export const checkMavenAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "maven"
  );
  const pom = path.join(assets, "pom.xml");
  const githubWorkflow = path.join(
    assets,
    ".github",
    "workflows",
    "spotless.yaml"
  );
  const gitlabCi = path.join(assets, ".gitlab-ci.yml");
  requireTexts([
    {
      fragments: [
        "spotless-maven-plugin",
        "markdownlint-cli2",
        "maven-checkstyle-plugin"
      ],
      path: pom
    }
  ]);
  const spotlessVersion = readRequiredCapture(
    pom,
    /<artifactId>spotless-maven-plugin<\/artifactId>\s*<version>(?<version>[^<]+)<\/version>/u,
    "Spotless Maven version"
  );
  const checkstyleVersion = readRequiredCapture(
    pom,
    /<artifactId>checkstyle<\/artifactId>\s*<version>(?<version>[^<]+)<\/version>/u,
    "Checkstyle version"
  );
  const compilerRelease = Number(
    readRequiredCapture(
      pom,
      /<maven\.compiler\.release>(?<release>\d+)<\/maven\.compiler\.release>/u,
      "Maven compiler release"
    )
  );
  const githubRuntime = Number(
    readRequiredCapture(
      githubWorkflow,
      /java-version:\s*["']?(?<runtime>\d+)["']?/u,
      "GitHub Java runtime"
    )
  );
  const gitlabRuntime = Number(
    readRequiredCapture(
      gitlabCi,
      /image:\s*maven:3\.9-eclipse-temurin-(?<runtime>\d+)/u,
      "GitLab Maven Temurin runtime"
    )
  );
  const requiredRuntime = requiredJavaReleaseForCheckstyle(checkstyleVersion);
  if (!/^\d+\.\d+\.\d+$/u.test(spotlessVersion)) {
    fail(`[assetVersion] invalid Spotless Maven version: ${spotlessVersion}`);
  }
  if (!/^\d+\.\d+\.\d+$/u.test(checkstyleVersion)) {
    fail(`[assetVersion] invalid Checkstyle version: ${checkstyleVersion}`);
  }
  if (!Number.isSafeInteger(compilerRelease) || compilerRelease < 1) {
    fail(`[maven assets] invalid compiler release: ${pom}`);
  }
  if (githubRuntime !== requiredRuntime || gitlabRuntime !== requiredRuntime) {
    fail(
      `[maven assets] CI Java runtime must match Checkstyle compatibility release ${requiredRuntime}`
    );
  }
  if (githubRuntime <= compilerRelease) {
    fail("[maven assets] CI Java runtime must exceed the compiler release");
  }
  rejectTextFragments(pom, ["git-build-hook-maven-plugin", "core.hooksPath"]);
  checkGithubWorkflow(githubWorkflow, [
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "./mvnw validate -DspotlessFiles"
  ]);
  requireTexts([
    {
      fragments: ["spotless:", "./mvnw validate -DspotlessFiles"],
      path: gitlabCi
    }
  ]);
  console.error("[maven assets] OK");
};
