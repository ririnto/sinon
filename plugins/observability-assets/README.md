---
title: Observability Assets
description: >-
  Overview of the Observability Assets plugin, its included skills, and practical observability configuration workflow coverage.
---

Observability Assets is a shared, skill-first plugin for Prometheus alerting and Grafana dashboard configuration work in the Sinon universal marketplace.

## Purpose

- Provide reusable observability workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on version-controlled monitoring assets rather than UI-only click paths.
- Separate Prometheus and Grafana operator workflows from application-framework-specific observability guidance.

## Included Skills

- `prometheus-alert-rules`: alert rule authoring, recording-rule support, `promtool` validation, alert annotations, and low-noise operational alert design.
- `grafana-dashboards`: dashboard JSON structure, Grafana mixin and Jsonnet guidance, stable dashboard identity, panel/query organization, and Git-managed dashboard asset workflows.

## When to Use Which Skill

- PromQL alert rule design, severity labels, `for` windows, and `promtool` rule validation belong in Prometheus alert-rule guidance.
- Dashboard JSON authoring, Grafana mixin usage, stable `uid` handling, and Git-managed Grafana dashboard asset review belong in Grafana dashboard guidance.

Typical workflow:

1. Start with the operational question that needs an alert or dashboard.
2. Use Prometheus guidance when the task is deciding what should fire and how to validate the rule.
3. Use Grafana guidance when the task is deciding how to present the signal and how to manage the dashboard asset safely.
4. Keep application-framework-specific instrumentation concerns in framework-specific plugins rather than this plugin.

## Scope Boundaries

Observability Assets stays responsible for Prometheus rule files, PromQL-oriented alert patterns, `promtool` validation workflows, Grafana dashboard JSON, and Grafana mixin and Jsonnet-oriented dashboard generation.

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
