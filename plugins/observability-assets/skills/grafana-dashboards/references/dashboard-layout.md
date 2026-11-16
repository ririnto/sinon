# Dashboard Frameworks and Time Controls

## Best Practice Frameworks

Structure dashboards around proven observability frameworks so every panel answers a meaningful operational question.

### USE Method

Focus on Utilization, Saturation, and Errors for resource-centric views.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Utilization | How busy is the resource? | `node_cpu_seconds_total` (non-idle %) |
| Saturation | How much demand is queued? | `node_load_avg`, queue depth, conn count |
| Errors | How many operations failed? | Error rate, 5xx count, error ratio |

### RED Method

Focus on Rate, Errors, and Duration for request-driven services.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Rate | How many requests per second? | `rate(http_requests_total[5m])` |
| Errors | How many are failing? | `rate(http_requests_total{status=~"5.."}[5m])` |
| Duration | How long do requests take? | `histogram_quantile(0.99, ...)` |

### Four Golden Signals

Google's SRE framework: Latency, Traffic, Errors, and Saturation.

| Signal | Question | Example Metric |
| --- | --- | --- |
| Latency | How long do requests take? | Service response time percentiles |
| Traffic | How much demand is there? | Requests/sec, connections/sec |
| Errors | How many are failing? | Error rate, failure percentage |
| Saturation | How close to capacity? | CPU, memory, disk, connection pool usage |

## Time Picker and Repeat Behavior

Dashboard-level time controls:

| Field | Values | Purpose |
| --- | --- | --- |
| `timezone` | `"browser"`, `"utc"`, `"America/New_York"` etc. | Which timezone to use for display |
| `graphTooltip` | `0` (single), `1` (per series), `2` (all series) | Tooltip behavior on hover |
| `timepicker.hidden` | `true`, `false` | Hide the time picker UI entirely |
| `refresh` | `"5s"`, `"10s"`, `"30s"`, `"1m"`, `"5m"` etc. | Auto-refresh interval |

Repeat fields on a panel or row:

| Field | Values | Effect |
| --- | --- | --- |
| `repeat` | variable name | Which variable drives repetition |
| `repeatDirection` | `"h"`, `"v"` | Horizontal or vertical layout |
| `maxPerRow` | integer | Max panels per row (horizontal mode only) |

Keep one repeat driver per repeating panel or row.
If you still need a two-dimensional layout, compose it from supported building blocks such as a repeated row for the outer dimension and panels inside that row using regular variable interpolation for titles and queries.
