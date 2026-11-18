---
metadata:
  reference:
    Prometheus:
      version: 3.13.5
      license: Apache-2.0
      url:
        - https://prometheus.io/docs/prometheus/3.13/configuration/recording_rules/
        - https://prometheus.io/docs/prometheus/3.13/configuration/alerting_rules/
    Prometheus releases:
      url: https://github.com/prometheus/prometheus/releases
name: prometheus-alert-rules
description: >-
  Use for Prometheus alert and recording rules, firing timers, routing labels, annotations, and promtool check rules validation.
---

# Prometheus Alert Rules

Design and review Prometheus alert and recording rules around real operator symptoms, validate them with `promtool`, and keep rule definitions stable in version-controlled files.

## Task Focus

- Inspect the affected rules, consumers, and existing tests against the operator symptom that should page or ticket.
- Keep group placement, evaluation intervals, and the alert expression deliberate.
- Choose an explicit `for` window for sustained symptoms.
  Use `keep_firing_for` only when brief recoveries need suppression and the deployed version supports it.
- Keep labels bounded and routing-oriented, preferably literal, and keep annotations actionable.
  Keep link-like annotations such as `runbook_url` literal and trusted.
  Use lightweight Go templates in human-readable annotations where they improve operator clarity; template label values only for a specific reason and keep them stable and low-cardinality.
- Introduce recording rules when reuse or evaluation cost justifies them, not as a prerequisite to alert authoring.
- Validate edited rule files with `promtool check rules`.
  Use `alert-rule-testing` when timing, thresholds, or label changes need regression fixtures.

## Rule Structure

When authoring or reviewing rule groups, read [rule schema](./references/rule-schema.md) for fields, defaults, alert and recording examples, and naming conventions.
A file has `groups`; each group requires a unique name and rules; each rule has `alert` or `record` and requires `expr`.
Group labels are overridden by rule labels, and rule output limits apply independently: exceeding a limit discards that rule's output, records an evaluation error, and clears alert state for an alerting rule.
Alert expressions return an instant vector; each series creates an alert instance and an empty vector creates none.
Name recorded metrics `level:metric:operations`, order operations from newest outward, and drop `_total` when applying counter rate functions.
Choose `without` or `by` according to whether removed or retained labels are the clearer contract.

## Alert Lifecycle and Templates

When choosing firing timers or writing annotations, read [alert lifecycle and templates](./references/lifecycle-and-templates.md) for state transitions, flapping, template variables, types, and examples.
An active series becomes pending until `for` elapses continuously, then fires; a false condition resets pending.
After firing, `keep_firing_for` holds firing after the condition clears when supported.
Templates run once per alert instance; errors render as error strings. Keep routing labels stable and links literal.
A histogram sample is not a numeric scalar; numeric template comparisons apply only to float samples.

Use the deployment's `promtool` when available.
Before obtaining a binary, check official releases for the latest stable version compatible with the target server and rule features.
If unavailable, report validation as blocked rather than claiming the rule is ready.

## First Runnable Commands or Code Shape

Validate the actual rule file:

```sh
promtool check rules alerts/api-latency.rules.yaml

```

Use when: the rule is newly added or edited, `promtool` is available in `PATH`, and you need the first safe correctness check before deeper review.

For multiple files, strict mode, output examples, and diagnosis, read [rule validation details](./references/rule-validation.md).
Use `|-` for multiline PromQL; use `>-` for one-line expressions when plain scalars are fragile, and prefer readable block scalars for YAML-sensitive content.
Round only when the alert contract requires it and choose comparison operators and operand order to preserve the intended threshold.
Groups evaluate rules in declared order at their interval.

## Rule Examples

For error-rate, recording-rule-assisted, or multi-threshold rules, read [rule examples](./references/rule-examples.md) when adapting that alert pattern.

## Validate the Result

Review the affected rules with these checks:

- The alert maps to an operator symptom rather than one noisy infrastructure blip
- Rule groups and intervals are deliberate rather than left implicit by accident
- `for` is explicit and long enough to avoid obvious flapping
- `keep_firing_for` is present only when it reduces noisy false resolution
- Labels stay bounded and routing-oriented
- Annotations explain the symptom and include a usable runbook or remediation pointer
- Templates in label values have a specific reason and preserve stable, low-cardinality labels; link-like annotations such as `runbook_url` stay literal and trusted
- Recording-rule usage is justified by reuse, cost, or readability rather than habit
- The label set is a deliberate contract for downstream Alertmanager routing, grouping, and inhibition
- `promtool check rules` passes on the actual shipped file
- Dedicated alert-rule tests exist in the adjacent testing path when alert behavior must stay stable over time

Report syntax validation separately from behavioral test evidence.
Authoring and local validation do not authorize deploying rules or reloading Prometheus.
Obtain explicit authorization for the target before either runtime action.

## Output contract

Deliver the rule or review decision with its group and the condition that fires or clears the alert.
Report syntax and behavioral checks separately, including any downstream routing label that changes.

## References

| When working on... | Read... |
| --- | --- |
| deciding whether an alert needs dedicated regression coverage, recording-rule settle-time checks, or `for`/`keep_firing_for` lifecycle protection | [`./references/testing.md`](./references/testing.md) |
| adapting worked alert patterns or grouping related recording and alert rules | [`./references/alert-patterns.md`](./references/alert-patterns.md) |

## Invariants

- MUST tie the alert to a meaningful operator symptom.
- MUST validate edited rule files with `promtool check rules` before claiming they are ready.
- MUST keep routing labels and annotations explicit.
- MUST keep rule group structure, `for`, and `keep_firing_for` choices deliberate.
- MUST name alerts so the firing condition is obvious from the alert name itself.
- MUST preserve the intended threshold behavior when choosing precision and comparison operators.
- SHOULD keep templates lightweight because they execute on rule evaluation.
- SHOULD add `promtool test rules` coverage for important or subtle alerts.
- SHOULD keep expressions and labels stable enough for long-lived operations.

## Common Pitfalls

Use a symptom-aligned `for` window instead of paging on a spike; omit `for` for immediate firing or choose a meaningful duration.
Keep high-cardinality detail out of routing labels, maintain unique output label sets, and keep links literal and trusted.
Run syntax checks and add behavior tests when appropriate.

## Scope Boundaries

This skill covers rule groups, evaluation, timers, PromQL as used in alerts, labels, annotations, recording rules, and `promtool check rules`.
It does not cover dashboard layout, Alertmanager routing, deep PromQL design, dedicated test authoring, instrumentation setup, or incident processes.
