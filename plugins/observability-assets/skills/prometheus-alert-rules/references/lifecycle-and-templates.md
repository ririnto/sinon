# Alert Lifecycle and Templates

## Alert State Machine

Prometheus alerts transition through three states:

```text
inactive --> pending --> firing
   ^                    |
   |____________________|
           resolves

```

### Inactive

The expression evaluates to false or produces no series.
No alert state exists.

### Pending

The expression first evaluates to true.
A timer starts for the `for` duration.
During pending:

- The alert is visible in the UI as "pending"
- No notification is sent to Alertmanager
- If the expression becomes false before `for` elapses, the alert reverts to inactive
- If the expression stays true for the full `for` duration, the alert transitions to firing

### Firing

The `for` duration has been satisfied while the expression remains true.

- Alert instances are sent to Alertmanager
- Notifications are dispatched according to Alertmanager routing
- If the expression becomes false:
  - Without `keep_firing_for`: the alert immediately returns to inactive
  - With `keep_firing_for`: the alert stays in firing for the configured hold-open duration, then returns to inactive

## Flapping Behavior

An alert that oscillates around the threshold resets its pending timer each time it drops back to inactive.
Only a continuous `for` window of true evaluations causes a transition to firing.

Timing diagram for `for: 10m` with `keep_firing_for: 5m`:

```text
Time   0m   5m   10m  15m  20m  25m
Expr   T    T    T    T    F    F
State  pen  pen  fire fire fire inactive
                        ^--keep_firing_for holds here

```

## Go Template Variables in Annotations and Labels

These variables are available inside `{{ }}` template expressions in annotation values:

| Variable | Type | Description |
| --- | --- | --- |
| `$labels` | map | Labels of the alert instance (from the expression result). Access fields as `$labels.label_name`. |
| `$value` | `float64` or histogram | Alert sample value from `.Value`. |
| `$externalLabels` | map | External labels configured on the Prometheus server (`prometheus.yml` -> `global.external_labels`). |
| `$externalURL` | string | Configured external URL of the Prometheus server (`prometheus.yml` -> `global.external_url`). |

Template syntax reference.
The examples below cover plain variable interpolation in `summary`, value rendering in `description`, conditional rendering with `if`, and dashboard-link composition with `$externalURL`:

```yaml
annotations:
  summary: 'High error rate on {{ $labels.job }}'

  description: 'Error rate is {{ $value }} over the last 5 minutes.'

  description: >-
    {{ if eq $labels.severity "critical" }}Critical{{ else }}Warning{{ end }}:
    value is {{ $value }}

  dashboard: '{{ $externalURL }}/d/my-dashboard?var-job={{ $labels.job }}'

```

Template rules:

- Templates execute once per alert instance (per labelset)
- Template errors cause the entire annotation to render as the raw error string
- Use dot notation such as `{{ $labels.name }}` to access labels.
  - Bracket notation `{{ index $labels "name" }}` handles special characters
- Format a `float64` sample for display with a template function such as `printf "%.2f" $value`.
- Compare a sample numerically with functions such as `gt` only when it is a `float64`.
- A histogram sample is not a numeric scalar.
- Do NOT put templates in `labels` values unless you have a specific reason -- labels should be stable and low-cardinality
- Keep link-like annotations such as `runbook_url` as literal strings without template variables
