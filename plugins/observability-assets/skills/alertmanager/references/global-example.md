# Global Configuration Example

## Global Configuration

Global settings define defaults inherited by all receivers unless overridden locally.
Place credentials and shared endpoints here so individual receiver configs stay minimal.
The most commonly used fields are `resolve_timeout` (5m default), `smtp_smarthost`/`smtp_from` for email, `slack_api_url`, `pagerduty_url`, `http_config`, and `templates` glob patterns.
For the complete field reference including all credential fields, pairing rules, and inheritance behavior, see [`./global-config.md`](./global-config.md).

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
