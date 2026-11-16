---
metadata:
  reference:
    Project Reactor Core:
      version: 3.8.7
      url: https://projectreactor.io/docs/core/release/reference/aboutDoc.html
    Project Reactor Test:
      version: 3.8.7
      url:
        - https://repo.maven.apache.org/maven2/io/projectreactor/reactor-test/3.8.7/reactor-test-3.8.7.pom
        - https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifierOptions.html
        - https://projectreactor.io/docs/test/release/api/reactor/test/publisher/TestPublisher.Violation.html
    Project Reactor BOM:
      version: 2025.0.7
      url: https://repo.maven.apache.org/maven2/io/projectreactor/reactor-bom/2025.0.7/reactor-bom-2025.0.7.pom
name: reactor-testing
description: >-
  Write or fix Reactor publisher tests with StepVerifier, virtual time, TestPublisher, or PublisherProbe.
---

# Reactor Testing

## Official Baseline

- Use the official Project Reactor 3.8.x testing reference for this skill.
  - Reviewed against `reactor-test` 3.8.7 and `reactor-bom` 2025.0.7.
  These versions are documentation baselines, not dependency pins.
- Before adding or upgrading Reactor dependencies, check `io.projectreactor:reactor-bom` and `io.projectreactor:reactor-test` in Maven Central for the latest stable compatible release.
  Honor the project's existing platform, BOM, or pins and keep Reactor modules on the same managed release train.

Test Reactor publishers with the ordinary `reactor-test` path.

## Goal

This skill covers everyday `StepVerifier` flow, success/empty/error assertions, virtual time, request and cancellation assertions, `TestPublisher`, `PublisherProbe`, and ordinary post-verification checks.
Keep advanced `StepVerifierOptions`, context-specific expectations, timeout-heavy scenarios, and noncompliant publishers in blocker references.

Use `consumeRecordedWith(...)` only after `recordWith(...)` and before terminal verification in the verifier chain.

## Scope

Activate this skill for:

- testing a `Flux` or `Mono` with `StepVerifier`
- verifying value order, completion, emptiness, or errors
- testing time-based operators without waiting for real time
- controlling upstream emission manually with `TestPublisher`
- checking whether a fallback or alternate branch was subscribed with `PublisherProbe`
- asserting request, cancellation, or dropped/discarded signal behavior after verification

Do not activate for:

- ordinary `Flux` / `Mono` operator design as the main problem
- scheduler tuning or thread placement as the main problem
- framework-specific test clients such as Spring `WebTestClient`
- benchmarking or performance profiling
- transport-level or web-stack integration testing

When choosing reactor-test APIs for signals, demand, time, or subscription paths, read [the selection guide](references/testing-coverage.md).

## Operating rules

- Reuse sufficient existing coverage rather than imposing test-layer ratios or adding every layer for each change.
- Do not add task-specific test wrappers or test prose instructions when review is sufficient.
- Start with `StepVerifier.create(...)` unless time control is required.
- Use `withVirtualTime(...)` for delayed or interval-based publishers.
- Build the publisher lazily inside the virtual-time supplier.
- Make completion, emptiness, and error behavior explicit in the verifier.
- Use `TestPublisher` when the test must control upstream signals directly.
- Use `PublisherProbe` when the test concerns which branch was subscribed.
- Use `verifyThenAssertThat()` when dropped or discarded signals matter after execution.
- Keep framework-specific test clients out of this skill.

## Task Context

Read the publisher contract and related tests to identify the behavior that needs proof.
Reuse the repository's native runner, assertions, managed dependencies, and fixtures.
Choose signal, timing, demand, cancellation, or subscription assertions from acceptance criteria and regression risks.
Use the [reference table](#references) for advanced verifier options, timing edge cases, or deliberately noncompliant publishers.

## reactor-test quick reference

| Need | Default move | Why |
| --- | --- | --- |
| basic publisher verification | `StepVerifier.create(publisher)` | ordinary signal assertions |
| empty completion | `expectComplete()` | asserts no further signals |
| specific error type | `expectError(Class)` | keeps error intent explicit |
| complex value assertion | `assertNext(consumer)` | multi-property inspection per value |
| time-based operator | `StepVerifier.withVirtualTime(() -> publisher)` | avoids real delays |
| no events during a window | `expectSubscription().expectNoEvent(duration)` | subscription itself is an event |
| additional demand mid-test | `thenRequest(n)` | controls request flow |
| manual upstream source | `TestPublisher.create()` | emits signals on demand |
| branch selection check | `PublisherProbe.empty()` or `PublisherProbe.of(...)` | verifies subscription path |
| dropped/discarded checks | `verifyThenAssertThat()` | post-execution assertions |
| recorded-value inspection | `recordWith(...).consumeRecordedWith(...)` before terminal verification | inspect recorded `onNext` values during the scenario |

## Examples

When testing signals, virtual time, demand, cancellation, probes, or post-verification assertions, read [the examples](references/testing-examples.md).

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| forgetting to call `verify()` or a shortcut | the scenario never executes | end every verifier chain with `verify*` |
| building the virtual-time publisher outside the supplier | timers are created too early | create the publisher inside `withVirtualTime(...)` |
| using real time for delayed publishers | tests become slow or flaky | use virtual time |
| using `StepVerifier` when the real question is branch selection | signal assertions miss subscription-path intent | use `PublisherProbe` |
| asserting complicated upstream timing without control | behavior stays nondeterministic | use `TestPublisher` |

## Completion

Run the affected native tests and fix failures caused by the authorized change.
Confirm each verifier executes and terminates with the expected completion, error, or cancellation.
Test only the relevant signal, time, request, branch, or discard contract, not every reactor-test API.

## References

| Open this when... | Reference |
| --- | --- |
| basic verifier flow is not enough and you need scenario options, context expectations, or richer post-verification assertions | [Advanced StepVerifier](references/stepverifier-advanced.md) |
| the blocker is timeout behavior or virtual-time failure cases | [Testing Errors and Edge Cases](references/errors-and-edge-cases.md) |
| the blocker is a noncompliant `TestPublisher` or deliberate spec-edge behavior | [Noncompliant TestPublishers](references/noncompliant-testpublisher.md) |

## Result

Report the behavior proved, exact command and result, and any unverified scheduler or integration boundary.
Do not stop at a failing test when the task also authorizes the fix.
