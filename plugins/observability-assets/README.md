---
description: >-
  Overview of the Observability Assets plugin, its included skills, and practical observability configuration workflow coverage.
---

# Observability Assets

Observability Assets is a shared, skill-first plugin for Prometheus and Grafana asset authoring, routing, testing, and provisioning work in the Sinon Claude marketplace.

## Purpose

- Provide reusable observability workflows that remain portable across Claude Code plugin installations.
- Keep skills practical, example-driven, and focused on version-controlled monitoring assets rather than UI-only click paths.
- Separate Prometheus and Grafana operator workflows from application-framework-specific observability guidance.

## Verified Upstream Baseline

This review checked these upstream versions on 2026-09-11:

- Prometheus 3.14.0, released on 2026-08-17 and published on GitHub on 2026-08-18.
- Alertmanager 0.34.0, released and published on GitHub on 2026-08-16.
- Grafana 13.2.1, published on GitHub on 2026-09-02.

The release date is used when upstream states one.
Otherwise, the GitHub publication date is reported.

Treat these as documentation review baselines, not dependency pins.
Target deployments may stay on an older supported line, but version-sensitive features need a local version check before use.

## Included Skills

- `prometheus-alert-rules`: alert rule authoring, recording-rule support, burn-rate and SLO page alerts, `promtool` validation, alert annotations, and low-noise operational alert design.
- `grafana-dashboards`: dashboard JSON structure, SLO visibility panels, Grafana mixin and Jsonnet guidance, stable dashboard identity, panel/query organization, and Git-managed dashboard asset workflows.
- `promql`: PromQL selector, SLI and error-budget queries, aggregation, function choice, vector matching, and alert-vs-dashboard query review.
- `alertmanager`: Alertmanager route trees, receivers, grouping, inhibition, mute intervals, and notification templates.
- `alert-rule-testing`: `promtool test rules`, fixture design, eval timing, SLO burn-rate alert regression, and alert correctness checks.
- `dashboard-provisioning`: Grafana dashboard provider YAML, folder organization, SLO dashboard delivery, file-wins behavior, and dashboard-as-code delivery.

## Included Agents

- `observability-architect`: alerting, dashboard, SLO, and metrics architecture decisions.

`observability-architect` is a read-only leaf domain router for cross-skill observability decisions.
It may load observability skills but does not delegate to other agents.

## When to Use Which Skill

- PromQL expression design, aggregation, and vector matching belong in `promql`.
- Prometheus alert rule design, severity labels, `for` windows, and `promtool check rules` belong in `prometheus-alert-rules`.
- SLO work belongs in `promql` when shaping SLIs or error-budget queries, `prometheus-alert-rules` when burn-rate or page alerts should fire, `alert-rule-testing` when SLO alert correctness must be proven, and `grafana-dashboards` or `dashboard-provisioning` when the blocker is SLO visibility.
- Alert routing, receivers, grouping, inhibition, mute intervals, and notification text belong in `alertmanager`.
- `promtool test rules`, fixtures, eval timing, and regression checks belong in `alert-rule-testing`.
- Dashboard JSON authoring, Grafana mixin usage, stable `uid` handling, and dashboard review belong in `grafana-dashboards`.
- Provider YAML, dashboard-as-code folder structure, and file-wins drift handling belong in `dashboard-provisioning`.

Start with the operational question and select the skill for the affected asset.
Load supporting references only for the details the task needs.
Cross-skill work does not require running every workflow above.

Asset authoring and read-only review do not authorize deployment, runtime changes, or publication.
Each skill identifies its validation and runtime boundaries.

## Scope Boundaries

Observability Assets stays responsible for PromQL, Prometheus rule files, alert-rule tests, Alertmanager config, Grafana dashboard JSON, Grafana mixin and Jsonnet-oriented dashboard generation, Grafana dashboard provisioning, and SLO work expressed through those assets.

These topics fall outside Observability Assets' scope:

- application-framework-specific instrumentation and metrics emission details
- generic incident response or on-call process design not tied to alert-rule or dashboard assets

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Plugin Layout

```text
plugins/observability-assets/
+-- .claude-plugin/plugin.json
+-- README.md
+-- agents/
|   +-- observability-architect.md
+-- skills/
    +-- alert-rule-testing/
    +-- alertmanager/
    +-- dashboard-provisioning/
    +-- grafana-dashboards/
    +-- prometheus-alert-rules/
    +-- promql/
```

## Shipped Surfaces

- The plugin ships six reusable observability-asset skills under `skills/`.
- The plugin ships one plugin-root agent: `observability-architect` for alerting, dashboard, SLO, and metrics architecture decisions.

## Design Principles

- Prefer version-controlled monitoring assets over UI-only drift.
- Route to the smallest skill that matches the active observability asset.
- References are expected to contain concrete additive examples.

## Installation

Install from Sinon:

```sh
claude plugin install observability-assets@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/observability-assets
```

## Scope Notes

This plugin does not bundle ready-to-install Prometheus, Alertmanager, or Grafana asset files.
Skill examples are authoring patterns that must be adapted into the target repository's monitoring asset tree.
