# Alert Rule Examples

## Ready-to-Adapt Templates

High error-rate alert -- page on sustained user-visible failures instead of one short spike:

```yaml
groups:
  - name: api-errors
    rules:
      - alert: Api5xxRatioAbove5Percent
        expr: |-
          5 < 100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
            /
          sum(rate(http_requests_total{job="api"}[5m]))
        for: 10m
        labels:
          severity: page
          service: api
        annotations:
          summary: API 5xx ratio is high
          description: |-
            API 5xx ratio stayed above 5% for 10 minutes.
            Current value: {{ $value }}%.
          runbook_url: https://runbooks.example.com/api-high-error-rate

```

Use when: user-visible failures matter more than one host-level saturation signal.

Recording-rule-assisted alert -- reduce repeated query cost and stabilize the alert expression:

```yaml
groups:
  - name: api-recording
    rules:
      - record: job:http_requests:rate5m
        expr: >-
          sum by (job) (rate(http_requests_total[5m]))

  - name: api-alerts
    rules:
      - alert: Api5xxRatioAbove5Percent
        expr: |-
          5 < 100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
            /
          sum(job:http_requests:rate5m{job="api"})
        for: 10m
        labels:
          severity: page
          service: api
        annotations:
          summary: API 5xx ratio is high
          description: |-
            API 5xx ratio stayed above 5% for 10 minutes.
            Current value: {{ $value }}%.

```

Use when: the alert expression is reused or expensive enough to deserve one stable recording layer.

Multi-threshold alert with severity tiering via separate rules:

```yaml
groups:
  - name: disk-usage
    interval: 1m
    rules:
      - alert: DiskSpaceWarning
        expr: >-
          100 * (1 - node_filesystem_avail_bytes{fstype!="tmpfs"}
            /
          node_filesystem_size_bytes{fstype!="tmpfs"}) > 80
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: 'Low disk space on {{ $labels.instance }}'
          description: >-
            Disk usage is above 80% on {{ $labels.instance }} (device {{ $labels.device }}).
            Current: {{ $value }}%.

      - alert: DiskSpaceCritical
        expr: >-
          100 * (1 - node_filesystem_avail_bytes{fstype!="tmpfs"}
            /
          node_filesystem_size_bytes{fstype!="tmpfs"}) > 95
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: 'Critical disk space on {{ $labels.instance }}'
          description: >-
            Disk usage is above 95% on {{ $labels.instance }} (device {{ $labels.device }}).
            Current: {{ $value }}%.

```

Use when: the same symptom needs different urgency levels at different thresholds.
