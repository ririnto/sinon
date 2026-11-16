---
metadata:
  reference:
    Alertmanager:
      - version: v0.34.1
        license: Apache-2.0
        url:
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.1/docs/configuration.md
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.1/tracing/config.go
      - version: v0.34.0
        license: Apache-2.0
        url:
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.0/docs/configuration.md
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.0/docs/notifications.md
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.0/docs/notification_examples.md
          - https://raw.githubusercontent.com/prometheus/alertmanager/v0.34.0/LICENSE
    Alertmanager releases:
      url: https://github.com/prometheus/alertmanager/releases
    Alertmanager configuration:
      url: https://prometheus.io/docs/alerting/latest/configuration/
    Alertmanager notifications:
      url:
        - https://prometheus.io/docs/alerting/latest/notifications/
        - https://prometheus.io/docs/alerting/latest/notification_examples/
name: alertmanager
description: >-
  Use for Alertmanager configuration: route trees, receivers, grouping timers, inhibition, mute schedules, and notification templates.
---

# Alertmanager

Author and review Alertmanager configuration that routes alerts by recipient, groups them by label, and avoids noisy or misleading notifications.

## Matcher Compatibility

- Use modern Alertmanager matcher syntax as the common path: prefer the `matchers:` array form for routes and inhibition rules.
- Alertmanager supports fallback, UTF-8 strict, and classic matcher-parser modes.
  Write UTF-8-compatible matchers by default and keep older matcher fields only when the target deployment requires them.

## Task Focus

- Inspect the affected config and upstream alert labels against recipients and grouping behavior.
- Preserve a safe root receiver and add branches only for real label, severity, ownership, or environment differences.
- Adjust `group_wait`, `group_interval`, and `repeat_interval` only where the routing task needs different notification timing.
- Keep receiver mappings explicit and match labels that upstream alerts emit.
- Add inhibition or mute windows only to remove known noise without suppressing the primary symptom.
  Keep inhibition in top-level `inhibit_rules` and attach mute windows only to the affected routes.

## Minimal Setup

When creating a baseline, read [minimal route example](./references/minimal-route.md).

## First Runnable Commands or Code Shape

Start by validating the configuration file that will ship:

```sh
amtool check-config alertmanager.yml
```

Use the deployment's `amtool` when available.
Before obtaining a binary, check official releases for the latest stable version compatible with the target and schema.
If `amtool` is unavailable, report validation as blocked instead of claiming the config is ready.

## Route Tree

When reviewing routes, read [route tree details](./references/route-tree.md) for fields, defaults, traversal, and example.
Root MUST have a non-empty receiver, no match fields, mute or active intervals, or `continue: true`.
Children match in order; a matched child stops later siblings unless `continue: true`.
Active intervals do not affect matching: schedule suppression after a match does not resume traversal or fall back to root.
Root applies only if no child matched; continuing can fan out.
`group_by: ["..."]` groups by every label, while `[]` merges all alerts into one group; select groups and timers deliberately.

## Global Configuration

For inherited defaults or shared endpoints, read [global configuration](./references/global-config.md) and [example](./references/global-example.md).
Choose one of each inline credential and its `*_file` counterpart, and ensure referenced files are readable by the Alertmanager process.

## Suppression Scope: Config versus Runtime

Inhibition and route time intervals are config-time mechanisms; runtime silences are operational state replicated across the HA cluster.
Inspect silences via API or `amtool`, not by editing config.
Read-only inspection and local validation do not authorize runtime changes: obtain target authorization before reloading, sending test notifications, or creating or expiring silences.

## Inhibition Rules

For inhibition, read [rules](./references/inhibition-rules.md) and [example](./references/inhibition-example.md).
Use `source_matchers`, `target_matchers`, and deliberate `equal` labels; an alert instance cannot inhibit itself.
Do not use deprecated `source_match` or `target_match` in new configs.

## Label Matchers

For route or inhibition matchers, read [examples](./references/matcher-examples.md).
Use `=`, `!=`, `=~`, or `!~`; regex matchers are anchored and label names must match upstream labels.
Use UTF-8-compatible modern `matchers` for new configs.

## Time Intervals

For mute or active schedules, read [time intervals](./references/time-intervals.md) and [example](./references/interval-example.md).
Named top-level `time_intervals` are referenced by routes; specified fields are ANDed and omitted fields unconstrained.

## Receivers

For receivers read [receiver overview](./references/receiver-overview.md) and [receiver types](./references/receiver-types.md).
Receiver names MUST be unique and referenced by routes; a receiver without notification configs is a valid null sink.
Choose `send_resolved` for the receiver type.

## Notification Templates

When shaping notification content, read [notification templates](./references/notification-templates.md) and [template wiring](./references/template-wiring.md).
Template glob paths resolve relative to the config file; keep matched files on disk and wire them into receiver fields that support templates.
Check the receiver's text or HTML context before using `safeHtml`.

## Receiver and Schedule Examples

For mute intervals, Slack, PagerDuty, or webhooks, read [receiver and schedule examples](./references/receiver-examples.md).
Generic webhooks use Alertmanager's fixed notification data JSON; use the receiving service or an adapter for another payload shape.

## Validate the Result

Check the root receiver and constraints, child matchers against upstream labels, deliberate grouping timers, unique receiver names, and named intervals.
Ensure inhibition and schedules remove noise without hiding primary symptoms, and templates use available labels and annotations.
Check credential field exclusivity and process access to `*_file` paths, then run `amtool check-config` on the shipped file.
Report command results separately from routing and delivery evidence: a schema pass does not prove delivery.

## Output contract

Unless task, host, or dispatch requirements differ, return the config or review decision, intended route and receiver changes, validation results or tooling blockers, and remaining risks and upstream label dependencies.

## Shared Types

When configuring `http_config`, `oauth2`, `tls_config`, or `tracing_config`, read [shared types](./references/shared-types.md).

## Invariants

- MUST keep a deliberate root receiver, explicit matchers and receiver relationships, modern matcher syntax, and unique receiver names.
- SHOULD keep routes shallow, timers deliberate, and suppression focused on noise rather than the primary alert.

## Common Pitfalls

Use shallow routes backed by upstream labels; avoid high `group_wait` for urgent pages and prefer route tuning before inhibition.
Do not mix modern and deprecated matchers or both inline and file credentials.
Email receivers require `to`; explicit grouping avoids accidental per-alert notification floods.

## Scope Boundaries

This skill covers Alertmanager configuration and notification routing quality.
It excludes Prometheus rule authoring, PromQL, Grafana assets, Alertmanager deployment, clustering, and operational runbooks.
