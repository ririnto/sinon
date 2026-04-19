---
title: Observability Assets
description: >-
  Overview of the Observability Assets plugin, its included skills, and practical observability configuration workflow coverage.
---

Observability Assets is a shared, skill-first plugin for Prometheus and Grafana asset authoring, routing, testing, and provisioning work in the Sinon universal marketplace.

## Purpose

- Provide reusable observability workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on version-controlled monitoring assets rather than UI-only click paths.
- Separate Prometheus and Grafana operator workflows from application-framework-specific observability guidance.

## Included Skills

- `prometheus-alert-rules`: alert rule authoring, recording-rule support, `promtool` validation, alert annotations, and low-noise operational alert design.
- `grafana-dashboards`: dashboard JSON structure, Grafana mixin and Jsonnet guidance, stable dashboard identity, panel/query organization, and Git-managed dashboard asset workflows.
- `promql`: PromQL selector, aggregation, function choice, vector matching, and alert-vs-dashboard query review.
- `alertmanager`: Alertmanager route trees, receivers, grouping, inhibition, mute intervals, and notification templates.
- `alert-rule-testing`: `promtool test rules`, fixture design, eval timing, and alert regression checks.
- `dashboard-provisioning`: Grafana dashboard provider YAML, folder organization, file-wins behavior, and dashboard-as-code delivery.

## When to Use Which Skill

- PromQL expression design, aggregation, and vector matching belong in `promql`.
- Prometheus alert rule design, severity labels, `for` windows, and `promtool check rules` belong in `prometheus-alert-rules`.
- Alert routing, receivers, grouping, inhibition, mute intervals, and notification text belong in `alertmanager`.
- `promtool test rules`, fixtures, eval timing, and regression checks belong in `alert-rule-testing`.
- Dashboard JSON authoring, Grafana mixin usage, stable `uid` handling, and dashboard review belong in `grafana-dashboards`.
- Provider YAML, dashboard-as-code folder structure, and file-wins drift handling belong in `dashboard-provisioning`.

Typical workflow:

1. Start with the operational question that needs an alert or dashboard.
2. Use `promql` when the blocker is the query itself.
3. Use `prometheus-alert-rules` when the task is deciding what should fire.
4. Use `alert-rule-testing` when the blocker is proving alert behavior stays correct.
5. Use `alertmanager` when the blocker is routing or notification quality after the alert fires.
6. Use `grafana-dashboards` when the blocker is how to present the signal.
7. Use `dashboard-provisioning` when the blocker is how the dashboard is delivered and kept in sync from files.
8. Keep application-framework-specific instrumentation concerns in framework-specific plugins rather than this plugin.

## Scope Boundaries

Observability Assets stays responsible for PromQL, Prometheus rule files, alert-rule tests, Alertmanager config, Grafana dashboard JSON, Grafana mixin and Jsonnet-oriented dashboard generation, and Grafana dashboard provisioning.

Keep these in other plugins:

- application-framework-specific instrumentation and metrics emission details
- generic incident response or on-call process design not tied to alert-rule or dashboard assets

## Design Principles

- Prefer version-controlled monitoring assets over UI-only drift.
- Keep examples minimal but runnable in spirit.
- Route to the smallest skill that matches the active observability asset.
- Keep `SKILL.md` self-contained and usable on its own; use `references/` only for supplemental decision aids and longer notes.
- Reference files are expected to contain concrete additive examples and must not devolve into prose-only summaries.

## Installation

Install from Sinon:

```bash
/plugin install observability-assets@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/observability-assets
```
