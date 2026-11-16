# Receiver and Schedule Examples

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
