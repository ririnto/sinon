---
name: alertmanager
description: >-
  Use this skill when writing or reviewing Alertmanager config, routing alerts to the right receiver,
  tuning group_wait or repeat_interval, adding inhibition or mute intervals,
  configuring any notification receiver type (email, slack, pagerduty, webhook, opsgenie, telegram,
  discord, msteams, jira, mattermost, and others), authoring notification templates,
  or needing guidance on alertmanager.yml authoring and notification routing quality.
---

# Alertmanager

Author and review Alertmanager configuration that routes alerts clearly, groups them deliberately, and avoids noisy or misleading notifications. The common case is one root route, one small set of child routes, one deliberate receiver mapping, and one timing policy that batches related alerts without hiding urgent signal.

## Common-Case Workflow

1. Start from the alert-routing intent: who should receive which alerts, and which alerts should stay grouped together.
2. Define one root route with a safe default receiver.
3. Add child routes only where labels, severity, team ownership, or environment justify a branch.
4. Tune `group_wait`, `group_interval`, and `repeat_interval` deliberately so related alerts batch together without hiding urgent changes.
5. Keep receiver definitions explicit, and make sure routing labels from Prometheus alerts actually match the route tree you wrote.
6. Add inhibition or mute windows only when they remove known noise without suppressing the primary symptom; keep inhibition in top-level `inhibit_rules` and attach mute windows only to the routes they should affect.

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

This skill uses modern `matchers` array syntax as the baseline. For version-sensitive notes on time intervals and mute schedules, see [`./references/time-intervals.md`](./references/time-intervals.md).

## First Runnable Commands or Code Shape

Start by validating the configuration file that will actually ship:

```bash
amtool check-config alertmanager.yml
```

Use when: the config was just edited, `amtool` is available in `PATH`, and you need the first safe syntax and schema check. If `amtool` is unavailable, stop at a blocked validation state instead of claiming the config is ready.

## Route Schema

The route tree is the core of every Alertmanager config. Every alert enters at the root route and traverses downward through matching child routes.

### Root Route Constraints

The root route MUST satisfy these constraints (enforced by config validation):

- MUST have a non-empty `receiver`
- MUST NOT have `matchers`, `match`, or `match_re` fields
- MUST NOT have `mute_time_intervals` or `active_time_intervals`
- MUST NOT set `continue: true`

### Route Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `receiver` | string | yes (root) | -- | Receiver name that handles this route's alerts |
| `group_by` | list of string | no | `["alertname"]` | Labels to group alerts by; `"..."` groups on all labels |
| `group_wait` | duration | no | 30s | Wait time before sending first notification for a new group |
| `group_interval` | duration | no | 5m | Minimum interval between notifications for the same group |
| `repeat_interval` | duration | no | 4h | Minimum interval before re-sending a notification for the same group |
| `matchers` | list of string | no | -- | Modern matcher expressions (e.g., `severity="page"`) |
| `match` | map[string]string | no | -- | **Deprecated**. Exact label equality matching |
| `match_re` | map[string]string | no | -- | **Deprecated**. Regex label matching |
| `continue` | bool | no | false | If true, continue matching child routes after this route matches |
| `routes` | list of route | no | -- | Child routes evaluated in order after parent matches |
| `mute_time_intervals` | list of string | no | -- | Named time intervals during which this route is muted |
| `active_time_intervals` | list of string | no | -- | Named time intervals during which this route is active |

### Route Traversal Algorithm

Alertmanager evaluates the route tree as follows:

1. An alert arrives at the root route.
2. The root route always matches (it has no matchers).
3. For each child route in order:
   a. If the child has `active_time_intervals` and the current time falls outside ALL of them, skip this child.
   b. Evaluate the child's matchers against the alert's labels.
   c. If the child matches AND does not have `continue: true`, stop traversing deeper children. The alert is routed to this child's receiver.
   d. If the child matches AND has `continue: true`, record this child as a match but continue evaluating subsequent siblings.
4. After all children are checked, if no child matched, use the root route's own receiver.
5. If multiple children matched via `continue`, each matching child receives the alert independently.

Key consequence: `continue: true` allows an alert to fan out to multiple receivers. Without it, the first matching child wins and traversal stops.

### The `group_by` Special Value

Setting `group_by: ["..."]` groups alerts on ALL their labels. This means two alerts with any differing label value form separate groups. Use sparingly -- it creates many small groups and can flood receivers.

An empty `group_by: []` puts every alert into a single group per route. Useful when you want exactly one notification per route regardless of labels.

### Complete Route Example

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
    # Critical pages go to on-call with tight grouping
    - receiver: critical-pager
      group_wait: 10s
      group_interval: 1m
      repeat_interval: 2h
      matchers:
        - severity="critical"
        - alertname=~"NodeDown|InstanceDown"

    # Team-based routing with continue to allow severity override
    - receiver: team-api
      continue: true
      matchers:
        - team="api"
      routes:
        - receiver: api-critical
          matchers:
            - severity="critical"

    # Environment isolation
    - receiver: staging-notify
      matchers:
        - environment="staging"
      mute_time_intervals:
        - offhours

    # Active only during business hours
    - receiver: business-hours-only
      active_time_intervals:
        - business-hours
      matchers:
        - severity="warning"
```

## Global Configuration

Global settings define defaults inherited by all receivers unless overridden locally. Place credentials and shared endpoints here so individual receiver configs stay minimal.

### Global Fields

The most commonly-used global fields:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `resolve_timeout` | duration | 5m | Time after which an unresolved alert is declared resolved if not updated |
| `smtp_smarthost` | host:port | -- | SMTP server address for email notifications |
| `smtp_from` | string | -- | Sender email address for SMTP |
| `slack_api_url` | URL | -- | Slack API URL (for webhook-based Slack) |
| `pagerduty_url` | URL | https://events.pagerduty.com/v2/enqueue | PagerDuty Events API v2 endpoint |
| `http_config` | http_config | -- | Default HTTP client config for all receivers |
| `templates` | list of string | -- | Glob patterns for template file paths |

For the complete global configuration reference including all credential fields, pairing rules, and inheritance behavior, see [`./references/global-config.md`](./references/global-config.md).

### Global Config Example

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
  opsgenie_api_key_file: /etc/alertmanager/opsgenie-key
  telegram_bot_token_file: /etc/alertmanager/telegram-token
  http_config:
    tls_config:
      insecure_skip_verify: false
```

## Inhibition Rules

Inhibition mutes target alerts when a source alert is already firing and both share specified equal labels.

### Inhibition Rule Schema

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `name` | string | no | -- | Optional human-readable name for the rule |
| `source_matchers` | list of string | yes* | -- | Matchers that identify source alerts (modern syntax) |
| `target_matchers` | list of string | yes* | -- | Matchers that identify target alerts (modern syntax) |
| `equal` | list of string | yes | -- | Labels that must be identical between source and target |
| `source_match` | map[string]string | no | -- | **Deprecated**. Exact source label matching |
| `source_match_re` | map[string]string | no | -- | **Deprecated**. Regex source label matching |
| `target_match` | map[string]string | no | -- | **Deprecated**. Exact target label matching |
| `target_match_re` | map[string]string | no | -- | **Deprecated**. Regex target label matching |

At least one of `source_matchers`/`source_match`/`source_match_re` must be provided (same for target). Prefer `source_matchers` and `target_matchers`.

### Self-Inhibition Prevention

Alertmanager prevents an alert from inhibiting itself. A source alert never suppresses a target alert that is the exact same alert instance.

### Basic Inhibition Example

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

For guidance on choosing `equal` labels safely and reviewing source/target shape, see [`./references/inhibition-rules.md`](./references/inhibition-rules.md).

## Label Matchers

Matchers are the mechanism by which routes and inhibition rules select alerts based on label values.

### Modern Matcher Syntax (`matchers`)

Each matcher is a string using one of four operators:

| Operator | Meaning | Example |
| --- | --- | --- |
| `=` | Exact equality | `team="api"` |
| `!=` | Inequality | `environment!="production"` |
| `=~` | Regex match | `alertname=~".*Down"` |
| `!~` | Regex mismatch | `severity!~"info\|debug"` |

Matcher strings go into the `matchers:` array:

```yaml
matchers:
  - team="api"
  - severity="critical"
  - alertname=~"NodeDown|InstanceDown"
  - environment!="staging"
```

### UTF-8 Label Names

Modern Alertmanager supports UTF-8 label names (e.g., Chinese characters in label names). The transition from classic ASCII-only label names is handled transparently. Write label names as they appear in your Prometheus metrics.

### Deprecated Forms

- `match: { label: "value" }` -- exact equality only, replaced by `matchers: [label="value"]`
- `match_re: { label: ".*pattern.*" }` -- regex only, replaced by `matchers: [label=~".*pattern.*"]`

These deprecated forms still parse but will be removed before v1.0. Always write new configs with `matchers`.

## Time Intervals

Time intervals define named schedule windows used by `mute_time_intervals` and `active_time_intervals` on routes.

### Time Interval Schema

A top-level `time_intervals:` block contains named entries:

```yaml
time_intervals:
  - name: <string>              # required, unique identifier
    time_intervals:             # required, list of interval specs
      - <TimeIntervalSpec>
```

### TimeIntervalSpec Fields

Each entry within `time_intervals:` defines one schedule window:

| Field | Type | Description |
| --- | --- | --- |
| `times` | list of TimeRange | Time-of-day ranges (HH:MM format, exclusive end) |
| `weekdays` | list of WeekdayRange | Day-of-week ranges (sunday=0 .. saturday=6) |
| `days_of_month` | list of DayOfMonthRange | Day-of-month ranges (1-31, negative = from month end) |
| `months` | list of MonthRange | Month ranges (january=1 .. december=12) |
| `years` | list of YearRange | Year ranges (positive integers) |
| `location` | string | IANA timezone name (e.g., "America/New_York") |

All fields within a `TimeIntervalSpec` are ANDed together: an alert falls inside the interval only when it satisfies EVERY specified constraint. Omitted constraints are unconstrained (always pass).

### Sub-Field Types

**TimeRange**: `{ start_time: "HH:MM", end_time: "HH:MM" }`. End is exclusive. Valid range: `00:00` to `24:00`.

**WeekdayRange**: String like `"monday"`, `"monday:friday"`, or `"sunday:saturday"`. Full names or colon-separated ranges.

**DayOfMonthRange**: Integer range like `1`, `15`, `1:15`, or `-1:-3` (negative counts from month end). Range `-31` to `31`, excluding 0.

**MonthRange**: String like `"january"`, `"march:may"`, or `"9:12"`. Full names or integer ranges 1-12.

**YearRange**: Integer range like `"2024"`, `"2024:2026"`.

### Time Interval Examples

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

Month-specific maintenance window:

```yaml
time_intervals:
  - name: q1-maintenance
    time_intervals:
      - months:
          - january:march
        days_of_month:
          - 1:3
        times:
          - start_time: "02:00"
            end_time: "06:00"
```

For timezone pitfalls, split-window patterns, year-limited schedules, and version notes, see [`./references/time-intervals.md`](./references/time-intervals.md).

## Receivers Overview

A receiver is a named destination that sends notifications through one or more configured channels. Each receiver can define multiple notification configs of the same or different types.

### Receiver Schema

```yaml
receivers:
  - name: <string>              # required, unique identifier
    <type>_configs:             # one or more notification type blocks
      - ...
```

Every receiver MUST have a unique `name`. This name is referenced by `receiver:` in route blocks. A receiver with no notification configs is valid (acts as a null sink).

### Shared Inline Fields (NotifierConfig)

Every receiver type inherits these fields from the embedded `NotifierConfig`:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `send_resolved` | bool | varies by type | Whether to send notifications when alerts resolve |

Default `send_resolved` by receiver type:

- Email: `false`
- Slack: `false`
- WeChat: `false`
- All other types: `true`

### Available Receiver Types

| YAML Key | Name | Description |
| --- | --- | --- |
| `email_configs` | Email | SMTP email delivery |
| `slack_configs` | Slack | Slack messaging (webhook or app token) |
| `pagerduty_configs` | PagerDuty | PagerDuty Events API v2 |
| `webhook_configs` | Webhook | Generic HTTP webhook POST |
| `opsgenie_configs` | OpsGenie | OpsGenie alerting |
| `victorops_configs` | VictorOps | Splunk On-Call (formerly VictorOps) |
| `pushover_configs` | Pushover | Pushover mobile notifications |
| `wechat_configs` | WeChat | WeChat Work messages |
| `sns_configs` | SNS | AWS SNS notifications |
| `telegram_configs` | Telegram | Telegram Bot API |
| `discord_configs` | Discord | Discord webhooks |
| `msteams_configs` | MS Teams | Microsoft Teams (legacy connector) |
| `msteamsv2_configs` | MS Teams V2 | Microsoft Teams (workflow bot) |
| `jira_configs` | Jira | Jira issue creation |
| `rocketchat_configs` | Rocket.Chat | Rocket.Chat messages |
| `mattermost_configs` | Mattermost | Mattermost webhooks |
| `webex_configs` | Webex | Cisco Webex messages |
| `incidentio_configs` | Incident.io | Incident.io incident creation |

For complete field schemas for every receiver type, see [`./references/receiver-types.md`](./references/receiver-types.md).

## Ready-to-Adapt Templates

Team route branch -- route critical API alerts to a dedicated receiver:

```yaml
route:
  receiver: platform-default
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
        - severity="page"

receivers:
  - name: platform-default
  - name: api-pager
```

Use when: one team owns a clearly labeled alert stream.

Grouping defaults -- batch related alerts before the first notification:

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - service
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
```

Use when: you need a sane default notification cadence before adding more routes.

Basic inhibition -- suppress a lower-severity symptom when a stronger alert already explains it:

```yaml
inhibit_rules:
  - source_matchers:
      - severity="page"
    target_matchers:
      - severity="ticket"
    equal:
      - service
      - cluster
```

Use when: multiple alerts describe the same outage and the lower-severity signal would only add noise.

Minimal notification template -- load one template file and render one stable summary from common labels:

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

Use when: the route is already correct and the common path only needs one small template surface for clearer notifications. Keep the template file on disk at the path matched by `templates:` so Alertmanager can actually load it, and wire it through a receiver field that actually supports templated strings.

Mute interval on a route -- suppress notifications during a scheduled window:

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

Webhook receiver with custom payload:

```yaml
receivers:
  - name: custom-webhook
    webhook_configs:
      - url: https://hooks.example.com/alertmanager
        send_resolved: true
        max_alerts: 0
        payload:
          text: '{{ template "slack.default.text" . }}'
```

For complete receiver configurations covering all 18 types (Telegram, Discord, Mattermost, Jira, OpsGenie, VictorOps, SNS, WeChat, Pushover, Rocket.Chat, Webex, MS Teams, incident.io, and email with full SMTP auth), see [`./references/receiver-types.md`](./references/receiver-types.md).

## Notification Templates

Templates control the content of notification messages. They use Go template syntax with Alertmanager-specific data structures and functions.

### Template Loading

Template files are loaded from paths listed under the top-level `templates:` key:

```yaml
templates:
  - '/etc/alertmanager/templates/*.tmpl'
  - '/etc/alertmanager/templates/custom/*.tmpl'
```

Paths are resolved relative to the config file location. Globs are supported. Alertmanager ships built-in templates (`default.tmpl`, `email.tmpl`) that provide default rendering for every receiver type.

### Available Data Fields

The root template data object (`.`) provides these top-level fields:

- `.Receiver` -- receiver name
- `.Status` -- `"firing"` or `"resolved"`
- `.Alerts` -- container with `.Firing` and `.Resolved` alert lists
- `.GroupLabels` / `.CommonLabels` / `.CommonAnnotations` -- shared label/annotation KV sets
- `.ExternalURL` -- Alertmanager instance URL
- `.NotificationReason` -- why the notification was sent

Each alert within `.Alerts.Firing` / `.Alerts.Resolved` exposes `.Labels`, `.Annotations`, `.StartsAt`, `.EndsAt`, `.GeneratorURL`, and `.Fingerprint`. KV objects support `.SortedPairs()`, `.Names()`, `.Values()`, `.Remove(keys)`, and `.String()`.

For the complete data structure reference, all 19 template functions with signatures and examples, built-in template names by receiver type, and defensive template patterns, see [`./references/notification-templates.md`](./references/notification-templates.md).

### Minimal Template Wiring Example

See the "Minimal notification template" entry in Ready-to-Adapt Templates above for the complete wiring pattern.

## Validate the Result

Validate the common case with these checks:

- the root route has a deliberate default receiver
- the root route has no matchers, no mute_time_intervals, no active_time_intervals, and no `continue: true`
- every child route matches on labels that the upstream alert rules really emit
- grouping timers batch related alerts without muting urgent signal
- receiver names are explicit, unique, and connected to the intended routes
- inhibition or mute logic removes known noise rather than hiding the primary symptom
- any mute interval or active interval used by a route is defined in the same config
- notification templates use labels and annotations that upstream alerts actually provide
- `*_file` fields reference files that exist and are readable by the Alertmanager process
- paired credential fields (e.g., `token` vs `token_file`) do not both contain values
- `amtool check-config` passes on the shipped config file

## Output contract

Return:

1. the recommended Alertmanager config or review decision
2. the intended route, receiver, inhibition, mute-interval, or template changes
3. validation results, including whether `amtool check-config` ran or is blocked by missing local tooling
4. remaining risks, assumptions, or upstream label-contract dependencies

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| complete schemas for all 18 receiver types | [`./references/receiver-types.md`](./references/receiver-types.md) |
| complete global config reference with all fields and constraints | [`./references/global-config.md`](./references/global-config.md) |
| http_config, oauth2, tls_config, tracing_config shared types | [`./references/shared-types.md`](./references/shared-types.md) |
| designing or reviewing inhibition logic | [`./references/inhibition-rules.md`](./references/inhibition-rules.md) |
| defining mute windows or active time schedules | [`./references/time-intervals.md`](./references/time-intervals.md) |
| shaping notification content with templates | [`./references/notification-templates.md`](./references/notification-templates.md) |

## Invariants

- MUST keep a deliberate default receiver at the root route.
- MUST keep the ordinary Alertmanager authoring path understandable from this file alone.
- MUST make matcher and receiver relationships explicit.
- SHOULD keep route trees shallow unless a deeper split is clearly justified.
- SHOULD use inhibition and mute windows to remove noise, not to hide the primary alert.
- SHOULD keep grouping timers deliberate and reviewable.
- MUST use `matchers` (modern syntax) over deprecated `match`/`match_re`.
- MUST ensure receiver names are unique across the entire config.

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
  - all 18 notification receiver type configurations
  - global configuration and shared infrastructure types
  - downstream notification quality and routing review
- Do not activate for:
  - Prometheus alert-rule authoring
  - PromQL query design
  - Grafana dashboard authoring or provisioning
  - Alertmanager binary deployment, clustering, or operational runbook design
