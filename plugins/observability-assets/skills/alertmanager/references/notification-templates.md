---
title: "Alertmanager Notification Templates"
description: "Open this when notification message shape or template data usage is the blocker."
---

# Alertmanager Notification Templates

Use this reference when the route is already correct, but the notification text itself still needs work.

## Template Focus

Use templates to improve operator clarity, not to build a second routing system in notification text.

Common data surfaces:

- `.Alerts.Firing`
- `.Alerts.Resolved`
- `.CommonLabels`
- `.CommonAnnotations`
- `.GroupLabels`

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

## Review Questions

- does the template improve actionability rather than add noise
- does it rely on labels or annotations that upstream alerts always provide
- would the same information be clearer as a stable alert annotation instead
