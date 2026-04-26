---
title: "Alertmanager Notification Templates"
description: "Open this when notification message shape, template data structures, template functions, or template rendering behavior is the blocker."
---

## Alertmanager Notification Templates

Use this reference when the route is already correct, but the notification text itself still needs work.

## Template Loading

Template files are loaded from paths listed under the top-level `templates:` key:

```yaml
templates:
  - '/etc/alertmanager/templates/*.tmpl'
  - '/etc/alertmanager/templates/custom/*.tmpl'
```

Paths are resolved relative to the config file location directory. Globs are supported. Alertmanager ships two built-in templates that are always loaded:

- `default.tmpl` -- default rendering templates for all receiver types
- `email.tmpl` -- email-specific default templates

Custom templates can override built-in definitions by using the same `define` name.

## Template Data Structures

The root data object (`.`) passed to every template is a `Data` struct with these fields:

### Top-Level Fields

| Field | Type | Description |
| --- | --- | --- |
| `.Receiver` | string | Name of the receiver handling this notification (regex-escaped) |
| `.Status` | string | Overall group status: `"firing"` or `"resolved"` |
| `.Alerts` | Alerts | Container with `.Firing()` and `.Resolved()` methods |
| `.GroupLabels` | KV | Labels used to form the alert group for routing |
| `.CommonLabels` | KV | Labels shared by ALL alerts in the group |
| `.CommonAnnotations` | KV | Annotations shared by ALL alerts in the group |
| `.ExternalURL` | string | External URL of the Alertmanager instance |

### Alert Object Fields

Each element within `.Alerts.Firing` and `.Alerts.Resolved` is an `Alert` struct:

| Field | Type | Description |
| --- | --- | --- |
| `.Status` | string | `"firing"` or `"resolved"` (per-alert status) |
| `.Labels` | KV | Label key-value pairs for this alert instance |
| `.Annotations` | KV | Annotation key-value pairs for this alert instance |
| `.StartsAt` | time.Time | Timestamp when the alert started firing |
| `.EndsAt` | time.Time | Timestamp when the alert was (or will be) resolved |
| `.GeneratorURL` | string | URL back to the originating Prometheus alert rule |
| `.Fingerprint` | string | Unique identifier for this alert (stable across reloads) |

### KV Methods

`KV` is the type used for `.Labels`, `.Annotations`, `.CommonLabels`, `.CommonAnnotations`, and `.GroupLabels`. It provides these methods:

| Method | Return Type | Description |
| --- | --- | --- |
| `.SortedPairs()` | Pairs | Returns sorted list of `Pair` structs (alertname always first) |
| `.Names()` | []string | Returns sorted list of label/annotation names |
| `.Values()` | []string | Returns sorted list of label/annotation values |
| `.Remove(keys)` | KV | Returns new KV excluding the given keys |
| `.String()` | string | Returns `"key1=val1, key2=val2"` representation |

### Pair Fields

Each element from `.SortedPairs()`:

| Field | Type | Description |
| --- | --- | --- |
| `.Name` | string | The label or annotation name |
| `.Value` | string | The label or annotation value |

### Alerts Methods

The `.Alerts` container provides filtering methods:

| Method | Return Type | Description |
| --- | --- | --- |
| `.Firing()` | []Alert | Subset of alerts with `Status == "firing"` |
| `.Resolved()` | []Alert | Subset of alerts with `Status == "resolved"` |

## Template Functions

Alertmanager extends Go templates with these custom functions. Standard Go template functions (`if`, `with`, `range`, `and`, `or`, `not`, `eq`, `ne`, `lt`, `le`, `gt`, `ge`, `printf`, `println`, `len`, `index`, etc.) remain available.

### String Functions

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `toUpper` | `toUpper(s) string` | `{{ "hello" \| toUpper }}` -> `"HELLO"` | Convert to uppercase |
| `toLower` | `toLower(s) string` | `{{ "HELLO" \| toLower }}` -> `"hello"` | Convert to lowercase |
| `title` | `title(s) string` | `{{ "hello world" \| title }}` -> `"Hello World"` | Title-case (American English conventions) |
| `trimSpace` | `trimSpace(s) string` | `{{ "  x  " \| trimSpace }}` -> `"x"` | Trim leading/trailing whitespace |

### Regex and Matching

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `match` | `match(pattern, text) bool` | `{{ match "^Node.*" .Labels.alertname }}` | Test regex match (returns bool) |
| `reReplaceAll` | `reReplaceAll(pattern, repl, text) string` | `{{ reReplaceAll "\\." "-" "a.b.c" }}` -> `"a-b-c"` | Replace all regex matches |

### Collection Functions

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `join` | `join(sep, []string) string` | `{{ .Labels.Names \| join ", " }}` | Join strings with separator (args inverted for pipeline use) |
| `stringSlice` | `stringSlice(s...string) []string` | `{{ stringSlice "a" "b" "c" }}` | Create a string slice from arguments |
| `append` | `append(slice, args...) []any` | `{{ $x := append $list "new" }}` | Append elements to a slice |
| `list` | `list(args...) []any` | `{{ list 1 "two" true }}` | Create a mixed-type list |
| `dict` | `dict(k1, v1, k2, v2, ...) map[string]any` | `{{ dict "key" "val" "n" 42 }}` | Build a map from alternating key-value args; requires even count; keys must be strings |

### JSON and Serialization

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `toJson` | `toJson(v) (string, error)` | `{{ .Alerts.Firing \| toJson }}` | Serialize any value to JSON string |

### Time Functions

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `date` | `date(format, t) string` | `{{ date "2006-01-02" .StartsAt }}` | Format time.Time with Go layout format |
| `tz` | `tz(name, t) (time.Time, error)` | `{{ tz "America/New_York" .StartsAt }}` | Convert time to a different timezone |
| `since` | `since(t) duration` | `{{ since .StartsAt }}` | Duration since the given time |
| `humanizeDuration` | `humanizeDuration(d) string` | `{{ humanizeDuration (since .StartsAt) }}` | Convert duration to human-readable string (e.g., "3h 25m") |

### HTML/URL Safety

| Function | Signature | Example | Description |
| --- | --- | --- | --- |
| `safeHtml` | `safeHtml(s) html.HTML` | `{{ "<b>bold</b>" \| safeHtml }}` | Mark string as safe HTML (bypass auto-escaping) |
| `safeUrl` | `safeUrl(s) html.URL` | `{{ "https://example.com?q=1" \| safeUrl }}` | Mark string as safe URL (bypass auto-escaping) |
| `urlUnescape` | `urlUnescape(s) (string, error)` | `{{ urlUnescape "%20" }}` -> `" "` | URL-decode a percent-encoded string |

## Built-in Template Names

Alertmanager's `default.tmpl` defines these templates (referenced by receiver defaults):

| Template Name | Used By | Default Content |
| --- | --- | --- |
| `slack.default.color` | Slack configs | Red for firing, green for resolved, other colors for other states |
| `slack.default.title` | Slack configs | `[Status] summary from CommonLabels` |
| `slack.default.text` | Slack configs | Firing/resolved alert counts with labels |
| `slack.default.fallback` | Slack configs | Plain-text fallback for non-rich notifications |
| `slack.default.username` | Slack configs | `"Alertmanager"` |
| `slack.default.pretext` | Slack configs | Empty |
| `slack.default.footer` | Slack configs | Empty |
| `slack.default.iconemoji` | Slack configs | `"bell"` |
| `slack.default.iconurl` | Slack configs | Empty |
| `slack.default.titlelink` | Slack configs | ExternalURL |
| `slack.default.callbackid` | Slack configs | Empty |
| `email.default.subject` | Email configs | `[Status] Firing N alerts, Resolved N alerts` |
| `email.default.html` | Email configs | HTML table of firing/resolved alerts |
| `pagerduty.default.description` | PagerDuty | Summary of firing/resolved alerts |
| `pagerduty.default.client` | PagerDuty | `"Alertmanager"` |
| `pagerduty.default.clientURL` | PagerDuty | ExternalURL |
| `opsgenie.default.message` | OpsGenie | Alert summary |
| `opsgenie.default.description` | OpsGenie | Detailed description |
| `opsgenie.default.source` | OpsGenie | `"prometheus"` |
| `wechat.default.message` | WeChat | Text-formatted alert summary |
| `wechat.default.to_user` | WeChat | `"@all"` |
| `wechat.default.to_party` | WeChat | Empty |
| `wechat.default.to_tag` | WeChat | Empty |
| `wechat.default.agent_id` | WeChat | Empty |
| `victorops.default.state_message` | VictorOps | Alert details |
| `victorops.default.entity_display_name` | VictorOps | Alert name |
| `victorops.default.monitoring_tool` | VictorOps | `"alertmanager"` |
| `pushover.default.title` | Pushover | `[status] alert summary` |
| `pushover.default.message` | Pushover | Alert body text |
| `pushover.default.url` | Pushover | ExternalURL |
| `sns.default.subject` | SNS | `[status] alert summary` |
| `sns.default.message` | SNS | Alert body text |
| `telegram.default.message` | Telegram | Formatted alert text |
| `discord.default.title` | Discord | `[status] alert summary` |
| `discord.default.message` | Discord | Alert body text |
| `msteams.default.title` | MS Teams | Alert title |
| `msteams.default.summary` | MS Teams | Alert summary |
| `msteams.default.text` | MS Teams | Alert body text |
| `msteamsv2.default.title` | MS Teams V2 | Alert title |
| `msteamsv2.default.text` | MS Teams V2 | Alert body text |
| `jira.default.summary` | Jira | `[status] alertname` |
| `jira.default.description` | Jira | Detailed alert description |
| `jira.default.priority` | Jira | Dynamic priority based on severity |
| `rocketchat.default.title` | Rocket.Chat | Alert title |
| `rocketchat.default.text` | Rocket.Chat | Alert body text |
| `rocketchat.default.emoji` | Rocket.Chat | Icon emoji |
| `rocketchat.default.iconurl` | Rocket.Chat | Icon URL |
| `mattermost.default.username` | Mattermost | `"Alertmanager"` |
| `mattermost.default.color` | Mattermost | Dynamic color by status |
| `mattermost.default.text` | Mattermost | Alert body text |
| `mattermost.default.title` | Mattermost | Alert title |
| `mattermost.default.titlelink` | Mattermost | ExternalURL |
| `mattermost.default.fallback` | Mattermost | Plain-text fallback |
| `webex.default.message` | Webex | Alert body text |

## Template Focus

Use templates to improve operator clarity, not to build a second routing system in notification text.

Common data surfaces for most templates:

- `.Alerts.Firing` -- currently active alerts
- `.Alerts.Resolved` -- recently resolved alerts
- `.CommonLabels` -- labels shared across all alerts in the group
- `.CommonAnnotations` -- annotations shared across all alerts
- `.GroupLabels` -- labels used for grouping

## Template Review Focus

After the common-path wiring is in place, review the template body itself for data-shape safety:

- prefer `.CommonLabels` and `.CommonAnnotations` when the group should read as one incident
- switch to `.Alerts.Firing` or `.Alerts.Resolved` only when the receiver must enumerate alert instances explicitly
- avoid assuming a label such as `service` exists unless the upstream alert contract guarantees it

Defensive pattern for optional labels:

```gotemplate
{{ define "alert.summary" }}
{{ .CommonLabels.alertname }}{{ with .CommonLabels.service }} for {{ . }}{{ end }}
{{ end }}
```

Per-alert expansion pattern when grouped labels are insufficient:

```gotemplate
{{ define "alert.instances" }}
{{ range .Alerts.Firing -}}
- {{ .Labels.instance }}: {{ .Annotations.summary }}
{{ end -}}
{{ end }}
```

Receiver-side JSON shape for webhook integrations:

```json
{
  "receiver": "api-webhook",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": { "alertname": "ApiDown", "severity": "page" },
      "annotations": { "summary": "API is down" },
      "startsAt": "2026-04-26T12:00:00Z",
      "endsAt": "0001-01-01T00:00:00Z",
      "generatorURL": "https://prometheus.example.com/graph?...",
      "fingerprint": "0123456789abcdef"
    }
  ],
  "groupLabels": { "alertname": "ApiDown" },
  "commonLabels": { "alertname": "ApiDown", "severity": "page" },
  "commonAnnotations": { "summary": "API is down" },
  "externalURL": "https://alertmanager.example.com"
}
```

Webhook receivers get Alertmanager's fixed JSON body based on the same `Data` fields; customize the outgoing format in the HTTP receiver or an intermediary service, not with a `webhook_configs` body field.

Time formatting with timezone conversion:

```gotemplate
{{ define "alert.timeinfo" }}
Started: {{ date "2006-01-02 15:04:05 MST" (.StartsAt | tz "America/New_York") }}
Duration: {{ humanizeDuration (since .StartsAt) }}
{{ end }}
```

Building structured data with `dict` for template reuse:

```gotemplate
{{ define "custom.incident_json" }}
{{ dict "alertname" .CommonLabels.alertname "status" .Status "count" (.Alerts.Firing | len) | toJson }}
{{ end }}
```

## Review Questions

- does the template improve actionability rather than add noise
- does it rely on labels or annotations that upstream alerts always provide
- would the same information be clearer as a stable alert annotation instead
- does it handle empty collections gracefully (e.g., zero firing alerts)
- does it use `safeHtml` only where the target receiver actually renders HTML
