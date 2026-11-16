# Route Tree Details

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
3. For each child route in order, evaluate its matchers against the alert's labels. `active_time_intervals` do not affect this match.
4. Record each matching child.
   Continue to later siblings only when the matched child has `continue: true`.
   The default is `false`.
5. After route matching, suppress notifications for matched routes whose `active_time_intervals` do not include the current time.
   Suppression does not resume sibling traversal or fall back to the root receiver.
6. Use the root receiver only when no child route matched.
7. If multiple children matched through `continue`, each route can send a notification when its schedule allows.

Key consequence: `continue: true` allows an alert to fan out to multiple receivers.
Without it, the first matching child wins and traversal stops.
`continue` is valid only on child routes.

### Complete Route Example

The first branches handle critical pages, continuing API-team routes, and deliberate staging suppression.
The final two warning routes select exactly one of their receivers through complementary business-hour schedules.
API-team warnings can also notify the earlier continuing team route.

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
      continue: true
      active_time_intervals:
        - business-hours
      matchers:
        - severity="warning"

    - receiver: after-hours
      mute_time_intervals:
        - business-hours
      matchers:
        - severity="warning"

time_intervals:
  - name: business-hours
    time_intervals:
      - location: Europe/Berlin
        weekdays:
          - monday:friday
        times:
          - start_time: "09:00"
            end_time: "17:00"
  - name: offhours
    time_intervals:
      - weekdays:
          - monday:friday
        times:
          - start_time: "00:00"
            end_time: "08:00"
```
