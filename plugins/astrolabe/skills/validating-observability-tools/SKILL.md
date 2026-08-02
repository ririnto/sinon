---
name: validating-observability-tools
description: Use when validating Alertmanager defaults, promtool rule selection, observability package manifests, or deterministic package evidence.
---

# Validating Observability Tools

## Anchor Each Fact

Treat checked-in facts as static package content and keep runtime behavior independent of upstream source trees.

Identify the observability fact, its authoritative source, and the package behavior that depends on it.

Read the owning [references/observability-facts.md](references/observability-facts.md) reference before changing a claim.

Use receiver-specific Alertmanager defaults rather than a global assumption.

Use the promtool `--run` expression as a repeatable test-group regular expression and keep `-r` tied to TSDB human-readable output.

## Validate Package Boundaries

Validate JSON manifests, hook roles, hook matchers, package containment, and prohibited runtime behavior.

Keep tests independent of user-global paths and external process state.

Report unavailable external validators as skipped evidence rather than as a pass.

## Exercise Failure Boundaries

Exercise valid object input, malformed input, non-object input, absent input, missing root, and invalid role cases.

Test both successful and failure boundaries for package adapters.

Record command output, exit status, and any unavailable validator as evidence.

## Return the Evidence

Return the fact checked, authority path, package paths, commands, exit statuses, evidence references, and blockers.

State whether the evidence covers success, malformed input, missing input, and invalid configuration.
