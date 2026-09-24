---
metadata:
  reference:
    Alertmanager:
      - version: v0.34.1
        license: Apache-2.0
        url:
          - https://github.com/prometheus/alertmanager/blob/v0.34.1/docs/configuration.md
          - https://github.com/prometheus/alertmanager/blob/v0.34.1/tracing/config.go
      - version: v0.34.0
        license: Apache-2.0
        url:
          - https://github.com/prometheus/alertmanager/blob/v0.34.0/docs/configuration.md
          - https://github.com/prometheus/alertmanager/blob/v0.34.0/docs/notifications.md
          - https://github.com/prometheus/alertmanager/blob/v0.34.0/docs/notification_examples.md
          - https://github.com/prometheus/alertmanager/blob/v0.34.0/LICENSE
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

- Inspect the affected config and upstream alert labels against the intended recipients and grouping behavior.
- Preserve a safe root receiver and add branches only for real label, severity, ownership, or environment differences.
- Adjust `group_wait`, `group_interval`, and `repeat_interval` only where the routing task needs different notification timing.
- Keep receiver mappings explicit and match labels that upstream alerts emit.
- Add inhibition or mute windows only to remove known noise without suppressing the primary symptom.
  Keep inhibition in top-level `inhibit_rules` and attach mute windows only to the affected routes.

## Minimal Setup

Minimal route tree and receiver layout:

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - service
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
        - severity="page"

receivers:
  - name: platform-default
  - name: api-pager
```

Use when: you need one readable Alertmanager baseline with a default receiver and one label-based branch.

## First Runnable Commands or Code Shape

Start by validating the configuration file that will ship:

```sh
amtool check-config alertmanager.yml
```

Use this check after editing the config, with the deployment's `amtool` when available.
Before obtaining a new binary, check the official Alertmanager releases for the latest stable version compatible with the target server and config schema.
If `amtool` is unavailable, report validation as blocked instead of claiming the config is ready.

## Route Tree

The route tree is the core of every Alertmanager config.
Every alert enters at the root route and traverses downward through matching child routes.

### Root Route Constraints

The root route MUST satisfy these constraints (enforced by config validation):

- MUST have a non-empty `receiver`
- MUST NOT have `matchers`, `match`, or `match_re` fields
- MUST NOT have `mute_time_intervals` or `active_time_intervals`
- MUST NOT set `continue: true`

### Route Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `receiver` | string | -- | Receiver name that handles this route's alerts (required on the root route) |
| `group_by` | list of string | `["alertname"]` | Labels to group alerts by; `"..."` groups on all labels, `[]` merges every alert into one group |
| `group_wait` | duration | 30s | Wait time before sending first notification for a new group |
| `group_interval` | duration | 5m | Minimum interval between notifications for the same group |
| `repeat_interval` | duration | 4h | Minimum interval before re-sending a notification for the same group |
| `matchers` | list of string | -- | Modern matcher expressions (e.g., `severity="page"`) |
| `continue` | bool | false | If true, continue matching child routes after this route matches |
| `routes` | list of route | -- | Child routes evaluated in order after parent matches |
| `labels` | map[string]string | -- | Template labels merged from parent to child; child values override parent values and do not change alert grouping. |
| `mute_time_intervals` | list of string | -- | Named time intervals during which this route is muted |
| `active_time_intervals` | list of string | -- | Named time intervals during which this route is active |

Deprecated `match` (exact equality) and `match_re` (regex) fields still parse but MUST NOT be used in new configs.
`matchers:` replaces both.

### Route Traversal and `continue`

Alertmanager evaluates the route tree as follows:

1. An alert arrives at the root route.
2. The root route always matches (it has no matchers).
3. For each child route in order:
   - If the child has `active_time_intervals` and the current time falls outside every referenced interval, skip this child.
   - Evaluate the child's matchers against the alert's labels.
   - If the child matches and has `continue: true`, record this child as a match and continue evaluating subsequent siblings.
   - If the child matches and does not have `continue: true`, route the alert to this child's receiver and stop evaluating siblings.
4. After all children are checked, if no child matched, use the root route's own receiver.
5. If multiple children matched via `continue`, each matching child receives the alert independently.

Key consequence: `continue: true` allows an alert to fan out to multiple receivers.
Without it, the first matching child wins and traversal stops.
`continue` is valid only on child routes.

### Complete Route Example

This route tree sends critical node and instance pages to a fast pager, allows team API alerts to continue into a nested critical route, isolates staging warnings during off-hours, and sends one warning route only during business hours.

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - cluster
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - receiver: critical-pager
      group_wait: 10s
      group_interval: 1m
      repeat_interval: 2h
      matchers:
        - severity="critical"
        - alertname=~"NodeDown|InstanceDown"

    - receiver: team-api
      continue: true
      matchers:
        - team="api"
      routes:
        - receiver: api-critical
          matchers:
            - severity="critical"

    - receiver: staging-notify
      matchers:
        - environment="staging"
      mute_time_intervals:
        - offhours

    - receiver: business-hours-only
      active_time_intervals:
        - business-hours
      matchers:
        - severity="warning"
```

## Global Configuration

Global settings define defaults inherited by all receivers unless overridden locally.
Place credentials and shared endpoints here so individual receiver configs stay minimal.
The most commonly used fields are `resolve_timeout` (5m default), `smtp_smarthost`/`smtp_from` for email, `slack_api_url`, `pagerduty_url`, `http_config`, and `templates` glob patterns.
For the complete field reference including all credential fields, pairing rules, and inheritance behavior, see [`./references/global-config.md`](./references/global-config.md).

```yaml
global:
  resolve_timeout: 5m
  smtp_smarthost: smtp.example.org:587
  smtp_from: alertmanager@example.org
  smtp_require_tls: true
  smtp_auth_username: alertmanager
  smtp_auth_password_file: /etc/alertmanager/smtp-password
  slack_app_token_file: /etc/alertmanager/slack-token
  pagerduty_url: https://events.pagerduty.com/v2/enqueue
  http_config:
    tls_config:
      insecure_skip_verify: false
```

## Suppression Scope: Config versus Runtime

Alertmanager stops notifications through three mechanisms:

- Inhibition (config): top-level `inhibit_rules` mute a target alert while a matching source alert fires.
- Mute and active time intervals (config): `mute_time_intervals` and `active_time_intervals` on routes suppress by schedule.
- Silences (runtime): created through the Alertmanager API (`POST /api/v2/silences`) or `amtool silence add`, matched by label matchers with a start and end time, and stored in Alertmanager state that is replicated across the HA cluster.

This skill owns the two config-time mechanisms.
Runtime silences are operational state outside config authoring.
Inspect them through the API or `amtool`, never by editing the config file.
Read-only inspection and local `amtool check-config` validation do not authorize runtime changes.
Obtain explicit authorization for the target before reloading config, sending test notifications, or creating or expiring silences.

## Inhibition Rules

Inhibition mutes target alerts when a source alert is already firing and both share specified equal labels.

```yaml
inhibit_rules:
  - name: severity-suppression
    source_matchers:
      - severity="critical"
    target_matchers:
      - severity="warning"
    equal:
      - alertname
      - cluster
      - namespace
```

A rule needs `source_matchers`, `target_matchers`, and `equal` labels that must be identical between source and target.
Deprecated `source_match`/`target_match` map forms still parse but MUST NOT be used in new configs.
Alertmanager prevents an alert from inhibiting itself: a source alert never suppresses the exact same alert instance.
For guidance on choosing `equal` labels safely and reviewing source/target shape, see [`./references/inhibition-rules.md`](./references/inhibition-rules.md).

## Label Matchers

Matchers select alerts by label values for routes and inhibition rules.
Each matcher is a string using one of four operators: `=` (exact), `!=` (negated exact), `=~` (anchored regex), `!~` (negated anchored regex).

```yaml
matchers:
  - team="api"
  - severity="critical"
  - alertname=~"NodeDown|InstanceDown"
  - environment!="staging"
```

Modern Alertmanager supports UTF-8 label names and handles the transition from classic ASCII-only names transparently.
Write label names as they appear in your Prometheus metrics.

## Time Intervals

Time intervals define named schedule windows used by `mute_time_intervals` and `active_time_intervals` on routes.
A top-level `time_intervals:` block holds named entries, and each entry defines one window with `times`, `weekdays`, `days_of_month`, `months`, `years`, and `location` fields.
All specified fields are ANDed together.
Omitted fields are unconstrained.

Business hours in Berlin timezone:

```yaml
time_intervals:
  - name: eu-business-hours
    time_intervals:
      - location: Europe/Berlin
        weekdays:
          - monday:friday
        times:
          - start_time: "09:00"
            end_time: "17:00"
```

For the full field schemas, range syntax, timezone pitfalls, split-window patterns, and version notes, see [`./references/time-intervals.md`](./references/time-intervals.md).

## Receivers Overview

A receiver is a named destination that sends notifications through one or more configured channel blocks.
Every receiver MUST have a unique `name`, referenced by `receiver:` in route blocks.
A receiver with no notification configs is valid (acts as a null sink).

Common channel blocks: `email_configs`, `slack_configs`, `pagerduty_configs`, `webhook_configs`, `opsgenie_configs`, and `telegram_configs`.
Alertmanager supports 18 receiver types in total, including SNS, Discord, both MS Teams variants, Jira, Rocket.Chat, Mattermost, Webex, VictorOps, Pushover, WeChat, and incident.io.
Default `send_resolved` is `false` for email, Slack, WeChat, and Rocket.Chat, and `true` for all other receiver types.
For complete field schemas for every receiver type, see [`./references/receiver-types.md`](./references/receiver-types.md).

## Notification Templates

Templates control notification content using Go template syntax with Alertmanager data structures.
Template files load from glob paths under the top-level `templates:` key, resolved relative to the config file location.

The root template data object provides `.Receiver`, `.Status` (`"firing"` or `"resolved"`), `.Alerts` (with `.Firing` and `.Resolved`), `.GroupLabels`/`.CommonLabels`/`.CommonAnnotations`, and `.ExternalURL`.
Each alert exposes `.Labels`, `.Annotations`, `.StartsAt`, `.EndsAt`, `.GeneratorURL`, and `.Fingerprint`.
For the complete data structure reference, all template functions, built-in template names by receiver type, and defensive template patterns, see [`./references/notification-templates.md`](./references/notification-templates.md).

Minimal template wiring -- load one template file and render one stable summary from common labels:

```yaml
global:
  smtp_smarthost: smtp.example.org:587
  smtp_from: alertmanager@example.org

templates:
  - templates/*.tmpl

receivers:
  - name: platform-default
    email_configs:
      - to: oncall@example.org
        html: '{{ template "alert.summary" . }}'
```

```gotemplate
{{ define "alert.summary" }}
{{ .CommonLabels.alertname }} for {{ .CommonLabels.service }}
{{ end }}
```

Keep the template file on disk at the path matched by `templates:` so Alertmanager can load it, and wire it through a receiver field that supports templated strings.

## Ready-to-Adapt Templates

Mute interval on a route: suppress notifications during a scheduled window.

```yaml
route:
  receiver: platform-default
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
      mute_time_intervals:
        - offhours

time_intervals:
  - name: offhours
    time_intervals:
      - weekdays:
          - monday:friday
        times:
          - start_time: "00:00"
            end_time: "08:00"
```

Use when: a route should stay valid, but notifications from that route should pause during a known schedule.

Slack receiver with app token:

```yaml
global:
  slack_app_token_file: /etc/alertmanager/slack-app-token

receivers:
  - name: slack-critical
    slack_configs:
      - channel: "#alerts-critical"
        title: '{{ template "slack.default.title" . }}'
        text: '{{ template "slack.default.text" . }}'
        send_resolved: true
```

PagerDuty receiver with routing key:

```yaml
global:
  pagerduty_url: https://events.pagerduty.com/v2/enqueue

receivers:
  - name: pd-oncall
    pagerduty_configs:
      - routing_key_file: /etc/alertmanager/pd-routing-key
        severity: critical
        class: prometheus-alert
        component: monitoring
        group: platform
```

Webhook receiver using Alertmanager's fixed JSON body:

```yaml
receivers:
  - name: custom-webhook
    webhook_configs:
      - url: https://hooks.example.com/alertmanager
        send_resolved: true
        max_alerts: 0
```

Generic webhook receivers always receive Alertmanager's fixed JSON body built from the notification `Data` object.
If the downstream service needs a different payload shape, put that transformation in the HTTP receiver or an intermediary adapter.

## Validate the Result

Review the changed config and its affected routes with these checks:

- the root route has a deliberate default receiver
- the root route has no matchers, no mute_time_intervals, no active_time_intervals, and no `continue: true`
- every child route matches on labels that the upstream alert rules emit
- grouping timers batch related alerts without muting urgent signal
- receiver names are explicit, unique, and connected to the intended routes
- inhibition or mute logic removes known noise rather than hiding the primary symptom
- any mute interval or active interval used by a route is defined in the same config
- notification templates use labels and annotations that upstream alerts provide
- `*_file` fields reference files that exist and are readable by the Alertmanager process
- paired credential fields (e.g., `token` vs `token_file`) do not both contain values
- `amtool check-config` passes on the shipped config file

Report the command result separately from routing or delivery evidence.
A syntax and schema pass does not prove notifications reach their intended recipients.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. the recommended Alertmanager config or review decision
2. the intended route, receiver, inhibition, mute-interval, or template changes
3. validation results, including whether `amtool check-config` ran or is blocked by missing local tooling
4. remaining risks, assumptions, or upstream label-contract dependencies

## References

| If the blocker is... | Read... |
| --- | --- |
| complete schemas for all 18 receiver types and their fields | [`./references/receiver-types.md`](./references/receiver-types.md) |
| complete global config reference with all fields and constraints | [`./references/global-config.md`](./references/global-config.md) |
| http_config, oauth2, tls_config, tracing_config shared types | [`./references/shared-types.md`](./references/shared-types.md) |
| designing or reviewing inhibition logic | [`./references/inhibition-rules.md`](./references/inhibition-rules.md) |
| defining mute windows or active time schedules | [`./references/time-intervals.md`](./references/time-intervals.md) |
| shaping notification content with templates | [`./references/notification-templates.md`](./references/notification-templates.md) |

## Invariants

- MUST keep a deliberate default receiver at the root route.
- MUST make matcher and receiver relationships explicit.
- MUST use `matchers` (modern syntax) over deprecated `match`/`match_re`.
- MUST ensure receiver names are unique across the entire config.
- SHOULD keep route trees shallow unless a deeper split is justified.
- SHOULD use inhibition and mute windows to remove noise, not to hide the primary alert.
- SHOULD keep grouping timers deliberate and reviewable.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| building a deep route tree before the label contract is stable | routing becomes hard to reason about and easy to break | start with one root route and only add branches backed by real labels |
| setting `group_wait` too high for urgent pages | the first useful signal arrives too late | keep the initial wait short for paging routes |
| using inhibition where route tuning would be enough | valid alerts disappear behind suppression logic | prefer clearer routing and grouping before adding inhibition |
| writing matchers for labels that the alert rules do not emit | routes never match in production | verify the Alertmanager config against the upstream label contract |
| setting `continue: true` on the root route | config validation rejects this immediately | `continue` is only valid on child routes |
| mixing `matchers` with deprecated `match`/`match_re` in the same route | confusing precedence, deprecated forms will be removed | use only `matchers` everywhere |
| providing both inline credential and `*_file` variant | config validation rejects this | choose exactly one: inline value or file path |
| omitting `to` in email_configs | validation fails with missing address error | `to` is the only required field beyond NotifierConfig |
| using `group_by: ["..."]` without understanding the impact | creates one group per unique label combination, potentially flooding receivers | use specific label names unless per-alert grouping is intentional |
| assuming templates render in HTML context for all receivers | some receivers use plain text (email text body, Telegram) | check the receiver's expected format before using `safeHtml` |

## Scope Boundaries

- Activate this skill for:
  - Alertmanager route trees and receivers
  - grouping timers, matchers, inhibition, mute intervals, and templates
  - notification receiver type configurations
  - global configuration and shared infrastructure types
  - downstream notification quality and routing review
- Do not activate for:
  - Prometheus alert-rule authoring
  - PromQL query design
  - Grafana dashboard authoring or provisioning
  - Alertmanager binary deployment, clustering, or operational runbook design
