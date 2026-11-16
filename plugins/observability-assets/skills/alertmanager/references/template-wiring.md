# Notification Template Wiring

## Notification Templates

Templates control notification content using Go template syntax with Alertmanager data structures.
Template files load from glob paths under the top-level `templates:` key, resolved relative to the config file location.

The root template data object provides `.Receiver`, `.Status` (`"firing"` or `"resolved"`), `.Alerts` (with `.Firing` and `.Resolved`), `.GroupLabels`/`.CommonLabels`/`.CommonAnnotations`, and `.ExternalURL`.
Each alert exposes `.Labels`, `.Annotations`, `.StartsAt`, `.EndsAt`, `.GeneratorURL`, and `.Fingerprint`.
For the complete data structure reference, all template functions, built-in template names by receiver type, and defensive template patterns, see [`./notification-templates.md`](./notification-templates.md).

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
