# Spring Plugin Rules

These rules apply to work under `plugins/spring/`.

Normative keywords such as MUST, MUST NOT, SHOULD, and MAY follow RFC 2119 and RFC 8174.

## Review and Research Scope

- A Spring investigation or review that tracks current behavior MUST state that it targets the latest released version.
- A review pinned to a release MUST name that release.
- Review prompts and delegated research prompts SHOULD name the version target.
- Delegated subagent prompts SHOULD require verification against official documentation pages, Maven Central, or published artifact pages.
- Discovery tools MAY find source URLs; factual verification SHOULD use source pages or artifact metadata.

## Review Execution

- Formal Spring skill audits SHOULD run independent reviewers in parallel when the work has enough scope to benefit from it.
- Parallel reviewers MUST audit the same explicit scope.
- Plan review and implementation review check different risks; run both when the workstream needs both.
- Reviews that depend on external Spring facts SHOULD pair document review with source or artifact verification.
- Review-driven fixes SHOULD receive a follow-up review over the same scope.
- Review findings SHOULD be fixed before a Spring skill is treated as complete.
- Repeated review timeouts SHOULD lead to a fresh or narrower audit.

## Maven Coordinate Verification

- Ambiguous Spring artifact choices MUST be verified against Maven Central or the Sonatype artifact page.
- Official Spring documentation SHOULD accompany Maven verification.
- Maven artifact metadata decides which published coordinate to recommend.
- Spring Boot servlet-web starter guidance in this plugin MUST use `spring-boot-starter-webmvc` unless newer authoritative sources prove another starter.

## Spring BOM Versioning

- Examples using a Spring BOM MUST omit versions for managed child Spring dependencies.
- Spring BOM examples SHOULD show the BOM import once and keep managed Spring artifacts versionless.
- Library versions managed by Spring Boot or another Spring BOM SHOULD stay versionless unless the example demonstrates an override.
- Exact Spring versions MUST appear only when a document pins a concrete dependency, BOM, metadata version, or published compatibility fact.
- Feature and API guidance SHOULD use version-line wording such as `Spring Security 7`, `7.x`, or `current stable line` unless the behavior is patch-specific.
- This rule applies to Spring Framework, Spring Boot, and Spring-managed dependency examples under `plugins/spring/`.

## Frontmatter Documentation Links

- YAML frontmatter links for Spring skills SHOULD use stable documentation entrypoints.
- Put version-specific documentation links in the skill body when the task requires release-specific analysis.

## Java and Test Example Style

- Java method and constructor parameter definitions MUST stay on a single line.
- Java method and constructor call arguments MUST stay on a single line.
- Blank lines MUST NOT appear inside function or method bodies.
- Test examples MUST use JUnit 5 style unless a document targets a Spring 7 or Boot 4 path that explicitly needs JUnit 6.
- Multiple related assertions MUST use `assertAll`.

## Change Discipline

- Rules in this file take precedence over conflicting Spring skill examples.
- Examples SHOULD be updated when they conflict with this file.
