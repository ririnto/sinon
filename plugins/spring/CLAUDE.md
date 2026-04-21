# Spring Plugin Rules

These rules apply to work under `plugins/spring/`.

Normative keywords such as MUST, MUST NOT, SHOULD, and MAY are to be interpreted as described in RFC 2119 and RFC 8174.

## Review and Research Scope

- When a Spring investigation or review is intended to track the current release line, the request MUST state that it targets the latest released version.
- When a review is intentionally pinned to a specific release, the request MUST name that version explicitly.
- Review prompts and delegated research prompts SHOULD make the version target explicit rather than implying it.
- Delegated subagent prompts SHOULD direct the subagent to verify Spring facts against the actual official documentation pages or Maven repository / published artifact pages, rather than treating Context7, Exa, or similar aggregators as the primary source.
- Context7, Exa, or similar aggregators MAY be used to discover relevant source URLs, but they SHOULD NOT replace checking the actual documentation page or Maven repository / published artifact page when the task is factual verification.

## Review Execution

- Spring skill reviews MUST run `oracle` and `librarian` in parallel when the task is a formal audit or rereview.
- Parallel reviewers MUST audit the same explicit scope rather than adjacent or partially overlapping file sets.
- Plan review and implementation review MAY both be required in the same workstream; passing one MUST NOT be treated as a substitute for the other.
- When a review depends on external library or framework facts, the review step SHOULD run external library exploration in parallel with the reviewer.
- When review-driven fixes are applied, the same scope SHOULD be rereviewed before the skill is treated as complete.
- Review findings, including minor wording or consistency issues, SHOULD be fixed before a Spring skill is treated as complete.
- When background review sessions time out repeatedly, agents SHOULD prefer a fresh session or a narrower fresh audit instead of repeatedly reusing the stale session.

## Maven Coordinate Verification

- When Spring artifact selection is ambiguous, agents MUST verify the coordinate directly against Maven Central or the Sonatype artifact page before finalizing guidance.
- Official Spring documentation SHOULD be used alongside Maven verification, but Maven artifact metadata is the tie-breaker when the question is which published coordinate to recommend.
- For Spring Boot servlet-web starter guidance in this plugin, `spring-boot-starter-webmvc` MUST be treated as the correct starter unless newer authoritative sources prove otherwise.

## Spring BOM Versioning

- When a Spring BOM is already in use, version tags for managed child Spring dependencies MUST be omitted from examples.
- Spring BOM examples SHOULD show the BOM import once and keep managed Spring artifacts versionless underneath it.
- When a standalone BOM path is documented for a concrete implementation path, the example SHOULD include the concrete versionless modules required for that path, not only the BOM import.
- Library versions used with Spring MAY also be managed by Spring Boot or another imported Spring BOM; when that is the case, examples SHOULD omit the explicit library version unless the example is intentionally demonstrating an override.
- Exact Spring versions MUST appear only when the document is intentionally pinning a concrete dependency, BOM, metadata version, or published compatibility fact.
- Feature and API guidance SHOULD use version-line wording such as `Spring Security 7`, `7.x`, or `current stable line` unless the behavior is verified to be patch-specific.
- Minimum-version feature guidance MAY use since-style wording such as `Spring Security 7+` when an exact patch floor is not required.
- This rule applies to Spring Framework, Spring Boot, and other Spring-managed dependency examples under `plugins/spring/` unless the example is intentionally demonstrating a non-BOM setup.

## Frontmatter Documentation Links

- YAML frontmatter links for Spring skills MUST use stable, unversioned documentation entrypoints.
- Frontmatter links MUST NOT include a version segment such as `/4.0.5/` or `/7.0/`.
- The preferred Spring Boot frontmatter links are:
  - `https://docs.spring.io/spring-boot/index.html`
  - `https://docs.spring.io/spring-boot/system-requirements.html`
  - `https://docs.spring.io/spring-boot/reference/index.html`
- Version-specific discussion MAY still appear in the body of the skill or in review notes when the task requires release-specific analysis.

## Java and Test Example Style

- Java method and constructor parameter definitions MUST stay on a single line.
- Java method and constructor call arguments MUST stay on a single line.
- Line breaks inside function or method bodies MUST be minimized.
- Consecutive blank lines inside function or method bodies MUST NOT appear.
- Test examples MUST use JUnit 5 style.
- When multiple assertions are evaluated together, they MUST be wrapped in `assertAll`.
- Inside `assertAll`, each line MUST contain exactly one assertion.

## Change Discipline

- When a rule in this file conflicts with an older local Spring skill example, the rule in this file takes precedence and the example SHOULD be updated.
