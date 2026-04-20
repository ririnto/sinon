---
title: "Alertmanager Receiver Types"
description: "Open this when configuring any notification receiver and need the complete field schema for a specific receiver type."
---

# Alertmanager Receiver Types

Use this reference when you need the complete field schema, required fields, defaults, and validation rules for any of the 18 supported receiver types. Every field is documented with its type, default value (if applicable), and whether it is required.

Every receiver type embeds `NotifierConfig` which provides one shared field:

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `send_resolved` | bool | varies by type | Send notification when alerts in the group resolve |

## email_configs

Sends notifications via SMTP email.

```yaml
receivers:
  - name: example-email
    email_configs:
      - to: oncall@example.org
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `to` | string | **yes** | -- | Recipient email address |
| `from` | string | no | global `smtp_from` | Sender email address override |
| `hello` | string | no | global `smtp_hello` | HELO/EHLO hostname override |
| `smarthost` | host:port | no | global `smtp_smarthost` | SMTP server address override |
| `auth_username` | string | no | global `smtp_auth_username` | SMTP auth username override |
| `auth_password` | secret | no | global `smtp_auth_password` | SMTP auth password (inline) |
| `auth_password_file` | string | no | global `smtp_auth_password_file` | Path to SMTP auth password file |
| `auth_secret` | secret | no | global `smtp_auth_secret` | SMTP AUTH secret (inline) |
| `auth_secret_file` | string | no | global `smtp_auth_secret_file` | Path to SMTP AUTH secret file |
| `auth_identity` | string | no | global `smtp_auth_identity` | SMTP auth identity string |
| `headers` | map[string]string | no | -- | Additional MIME headers (names case-insensitive, validated for duplicates) |
| `html` | string | no | `'{{ template "email.default.html" . }}'` | HTML body template |
| `text` | string | no | `""` | Plain text body template |
| `require_tls` | bool* | no | global `smtp_require_tls` | Require TLS for SMTP; nil inherits global |
| `tls_config` | tls_config | no | global `smtp_tls_config` | TLS configuration for SMTP |
| `force_implicit_tls` | bool* | no | nil (auto-detect by port) | Force implicit TLS (port 465) or explicit TLS/STARTTLS; nil auto-detects |
| `threading` | ThreadingConfig | no | -- | Email threading configuration |

\* Pointer type: omit to inherit global default.

### ThreadingConfig Sub-fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `enabled` | bool | false | Enable email threading via References/In-Reply-To headers |
| `thread_by_date` | string | -- | Must be `"none"` or `"daily"` when threading is enabled |

### Validation Rules

- `to` is always required.
- Header names are normalized to canonical MIME form; duplicate headers (case-insensitive) are rejected.
- When `threading.enabled` is true, custom `References` or `In-Reply-To` headers conflict and are rejected.
- At most one of `auth_password` / `auth_password_file`.
- At most one of `auth_secret` / `auth_secret_file`.

### Complete Example

```yaml
receivers:
  - name: email-oncall
    email_configs:
      - to: oncall@example.org
        from: alertmanager@example.org
        html: '{{ template "email.default.html" . }}'
        headers:
          X-Priority: "1"
          X-Auto-Response-Suppress: "OOF,DR,N,RN,BNR"
        require_tls: true
        threading:
          enabled: true
          thread_by_date: daily
```

---

## slack_configs

Sends notifications to Slack via webhook URL or Slack app token (chat.postMessage API).

```yaml
receivers:
  - name: example-slack
    slack_configs:
      - channel: "#alerts"
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `api_url` | URL | no | global `slack_api_url` | Slack API URL (webhook mode) |
| `api_url_file` | string | no | global `slack_api_url_file` | Path to file containing Slack API URL |
| `app_token` | secret | no | global `slack_app_token` | Slack bot/app token (for chat.postMessage) |
| `app_token_file` | string | no | global `slack_app_token_file` | Path to file containing app token |
| `app_url` | URL | no | global `slack_app_url` | Base URL for Slack app API calls |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `channel` | string | no | -- | Channel override (`#channel` or `@user`) |
| `username` | string | no | `'{{ template "slack.default.username" . }}'` | Bot username display name |
| `color` | string | no | `'{{ template "slack.default.color" . }}'` | Attachment color (sidebar) |
| `title` | string | no | `'{{ template "slack.default.title" . }}'` | Attachment title |
| `title_link` | string | no | `'{{ template "slack.default.titlelink" . }}'` | Attachment title link |
| `pretext` | string | no | `'{{ template "slack.default.pretext" . }}'` | Attachment pretext (above message) |
| `text` | string | no | `'{{ template "slack.default.text" . }}'` | Attachment body text |
| `message_text` | string | no | -- | Message text outside attachment (used with `update_message`) |
| `fields` | list of SlackField | no | -- | Attachment fields |
| `short_fields` | bool | no | false | Render all fields as short (side-by-side) |
| `footer` | string | no | `'{{ template "slack.default.footer" . }}'` | Attachment footer text |
| `fallback` | string | no | `'{{ template "slack.default.fallback" . }}'` | Fallback text (notifications off) |
| `callback_id` | string | no | `'{{ template "slack.default.callbackid" . }}'` | Action callback ID |
| `icon_emoji` | string | no | `'{{ template "slack.default.iconemoji" . }}'` | Bot icon emoji |
| `icon_url` | string | no | `'{{ template "slack.default.iconurl" . }}'` | Bot icon URL |
| `image_url` | string | no | -- | Attachment image URL |
| `thumb_url` | string | no | -- | Attachment thumbnail URL |
| `link_names` | bool | no | false | Find and link user/group names |
| `mrkdwn_in` | list of string | no | -- | Fields to process as markdown |
| `actions` | list of SlackAction | no | -- | Interactive action buttons |
| `update_message` | bool | no | false | Update existing messages instead of creating new ones |
| `timeout` | duration | no | -- | Max time for Slack API call (0 = no limit) |

### SlackAction Sub-fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `type` | string | **yes** | -- | Action type (e.g., `"button"`) |
| `text` | string | **yes** | -- | Button/element label text |
| `url` | string | cond.* | -- | URL for link buttons (excludes name/value) |
| `name` | string | cond.* | -- | Action name (required if no url) |
| `value` | string | no | -- | Action value |
| `style` | string | no | -- | Button style (`"primary"`, `"danger"`) |
| `confirm` | SlackConfirmationField | no | -- | Confirmation dialog |

\* Either `url` or `name` must be provided (mutually exclusive).

### SlackConfirmationField Sub-fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `text` | string | **yes** | -- | Confirmation dialog text |
| `title` | string | no | -- | Dialog title |
| `ok_text` | string | no | -- | Confirm button text |
| `dismiss_text` | string | no | -- | Dismiss button text |

### SlackField Sub-fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `title` | string | **yes** | -- | Field title |
| `value` | string | **yes** | -- | Field value |
| `short` | bool | no | -- | Display inline (true) or full-width (false) |

### Validation Rules

- At most one of `api_url` / `api_url_file`.
- At most one of `app_token` / `app_token_file`.
- Cannot use both `api_url`/`api_url_file` AND `app_token`/`app_token_file` together.
- `update_message: true` requires `api_url` set to `https://slack.com/api/chat.postMessage`.
- If using `app_token`, `http_config.authorization` cannot be separately set (the token becomes the bearer authorization).
- Default `send_resolved`: `false`.

### Complete Example

```yaml
receivers:
  - name: slack-critical
    slack_configs:
      - channel: "#alerts-critical"
        title: '{{ template "slack.default.title" . }}'
        text: '{{ template "slack.default.text" . }}'
        color: '{{ if eq .Status "firing" }}danger{{ else }}good{{ end }}'
        fields:
          - title: Severity
            value: "{{ .CommonLabels.severity }}"
            short: true
          - title: Service
            value: "{{ .CommonLabels.service }}"
            short: true
        send_resolved: true
        actions:
          - type: button
            text: "Acknowledge"
            url: "https://example.com/ack"
            style: danger
```

---

## pagerduty_configs

Sends notifications to PagerDuty via the Events API v2.

```yaml
receivers:
  - name: example-pagerduty
    pagerduty_configs:
      - routing_key: <key>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `service_key` | secret | cond.* | -- | **Deprecated**. Events API v1 service key |
| `service_key_file` | string | cond.* | -- | **Deprecated**. Path to v1 service key file |
| `routing_key` | secret | cond.* | -- | Events API v2 routing key (inline) |
| `routing_key_file` | string | cond.* | -- | Path to v2 routing key file |
| `url` | URL | no | global `pagerduty_url` | PagerDuty Events API endpoint |
| `client` | string | no | `'{{ template "pagerduty.default.client" . }}'` | Client name |
| `client_url` | string | no | `'{{ template "pagerduty.default.clientURL" . }}'` | Client URL |
| `description` | string | no | `'{{ template "pagerduty.default.description" . }}'` | Incident description |
| `details` | map[string]any | no | see below | Incident details payload |
| `images` | list of PagerdutyImage | no | -- | Attached images |
| `links` | list of PagerdutyLink | no | -- | Attached links |
| `source` | string | no | (defaults to client) | Incident source |
| `severity` | string | no | -- | Event severity (`critical`, `error`, `warning`, `info`) |
| `class` | string | no | -- | Incident class/event type |
| `component` | string | no | -- | Component name |
| `group` | string | no | -- | Grouping attribute |
| `timeout` | duration | no | -- | Max time for API call (0 = no limit) |

\* At least one of `routing_key`/`routing_key_file` OR `service_key`/`service_key_file` is required.

### Default Details

When not overridden, `details` includes these defaults:

| Key | Template |
| --- | --- |
| `firing` | `'{{ .Alerts.Firing \| toJson }}'` |
| `resolved` | `'{{ .Alerts.Resolved \| toJson }}'` |
| `num_firing` | `'{{ .Alerts.Firing \| len }}'` |
| `num_resolved` | `'{{ .Alerts.Resolved \| len }}'` |

Custom details are merged with these defaults; custom keys take precedence over defaults.

### PagerdutyImage Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `src` | string | Image URL |
| `alt` | string | Alt text |
| `href` | string | Link URL |

### PagerdutyLink Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `href` | string | Link URL |
| `text` | string | Link text |

### Validation Rules

- At least one of `routing_key`/`routing_key_file` or `service_key`/`service_key_file` must be provided.
- At most one of `routing_key` / `routing_key_file`.
- At most one of `service_key` / `service_key_file`.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: pd-oncall
    pagerduty_configs:
      - routing_key_file: /etc/alertmanager/pd-routing-key
        description: '{{ template "pagerduty.default.description" . }}'
        severity: critical
        class: prometheus-alert
        component: monitoring
        group: platform
        details:
          playbook: "https://runbook.example.com/{{ .CommonLabels.alertname }}"
        send_resolved: true
```

---

## webhook_configs

Sends notifications as generic HTTP webhook POST requests.

```yaml
receivers:
  - name: example-webhook
    webhook_configs:
      - url: https://example.com/webhook
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `url` | SecretTemplateURL | cond.* | -- | Webhook URL (supports Go templates) |
| `url_file` | string | cond.* | -- | Path to file containing webhook URL |
| `max_alerts` | uint64 | no | 0 (unlimited) | Maximum alerts per request (0 = unlimited) |
| `timeout` | duration | no | -- | Max time for HTTP call (0 = no limit) |
| `payload` | any | no | -- | Custom JSON payload (templates rendered before sending) |

\* Exactly one of `url` or `url_file` is required.

### Validation Rules

- Exactly one of `url` / `url_file` must be provided.
- The `url` field supports Go template syntax for dynamic URLs.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: custom-webhook
    webhook_configs:
      - url: https://hooks.example.com/alertmanager
        max_alerts: 10
        timeout: 10s
        send_resolved: true
        payload:
          text: '{{ template "slack.default.text" . }}'
```

---

## opsgenie_configs

Sends notifications to OpsGenie.

```yaml
receivers:
  - name: example-opsgenie
    opsgenie_configs:
      - api_key: <key>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_key` | secret | cond.* | global `opsgenie_api_key` | OpsGenie API key (inline) |
| `api_key_file` | string | cond.* | global `opsgenie_api_key_file` | Path to API key file |
| `api_url` | URL | no | global `opsgenie_api_url` | OpsGenie API base URL |
| `message` | string | no | `'{{ template "opsgenie.default.message" . }}'` | Alert message/title |
| `description` | string | no | `'{{ template "opsgenie.default.description" . }}'` | Alert description |
| `source` | string | no | `'{{ template "opsgenie.default.source" . }}'` | Alert source field |
| `details` | map[string]string | no | -- | Additional key-value details |
| `entity` | string | no | -- | Entity identifier (deduplication key) |
| `responders` | list of OpsGenieResponder | no | -- | Target responders |
| `actions` | string | no | -- | Comma-separated action names |
| `tags` | string | no | -- | Comma-separated tags |
| `note` | string | no | -- | Additional note |
| `priority` | string | no | -- | Priority (`P1`..`P5`) |
| `update_alerts` | bool | no | false | Update existing alerts instead of creating new ones |

\* At least one of `api_key` or `api_key_file` must be provided (globally or locally).

### OpsGenieResponder Sub-fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | cond.* | Responder ID |
| `name` | string | cond.* | Responder name |
| `username` | string | cond.* | Responder username |
| `type` | string | no | One of: `team`, `teams`, `user`, `escalation`, `schedule` |

\* At least one of `id`, `name`, or `username` is required per responder.

### Validation Rules

- At most one of `api_key` / `api_key_file`.
- Each responder must have at least one of `id`, `name`, `username`.
- Responder `type` must be one of: `team`, `teams`, `user`, `escalation`, `schedule` (or a valid template that renders to one).
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: opsgenie-oncall
    opsgenie_configs:
      - api_key_file: /etc/alertmanager/opsgenie-key
        message: '{{ template "opsgenie.default.message" . }}'
        description: '{{ template "opsgenie.default.description" . }}'
        priority: P3
        responders:
          - name: "oncall-team"
            type: team
          - username: "ops-user"
            type: user
        tags: prometheus,alertmanager
        send_resolved: true
```

---

## victorops_configs

Sends notifications to Splunk On-Call (formerly VictorOps).

```yaml
receivers:
  - name: example-victorops
    victorops_configs:
      - api_key: <key>
        routing_key: <key>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_key` | secret | cond.* | global `victorops_api_key` | VictorOps API key (inline) |
| `api_key_file` | string | cond.* | global `victorops_api_key_file` | Path to API key file |
| `api_url` | URL | **yes** | global `victorops_api_url` | VictorOps integration API URL |
| `routing_key` | string | **yes** | -- | Routing key for the VictorOps integration |
| `message_type` | string | no | `"CRITICAL"` | Incident message type |
| `state_message` | string | no | `'{{ template "victorops.default.state_message" . }}'` | State message body |
| `entity_display_name` | string | no | `'{{ template "victorops.default.entity_display_name" . }}'` | Display name for the entity |
| `monitoring_tool` | string | no | `'{{ template "victorops.default.monitoring_tool" . }}'` | Monitoring tool identification |
| `custom_fields` | map[string]string | no | -- | Custom incident fields |

### Reserved Custom Field Names

These keys are reserved and cannot appear in `custom_fields`:

`routing_key`, `message_type`, `state_message`, `entity_display_name`, `monitoring_tool`, `entity_id`, `entity_state`

### Validation Rules

- `routing_key` is always required.
- `api_url` is always required.
- At most one of `api_key` / `api_key_file`.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: victorops-oncall
    victorops_configs:
      - api_key_file: /etc/alertmanager/victorops-key
        api_url: https://alert.victorops.com/integrations/generic/20131114/alert/
        routing_key: my-routing-key
        message_type: CRITICAL
        custom_fields:
          environment: production
          team: platform
```

---

## pushover_configs

Sends notifications via Pushover mobile push service.

```yaml
receivers:
  - name: example-pushover
    pushover_configs:
      - user_key: <key>
        token: <token>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `user_key` | secret | cond.* | -- | Pushover user key (inline) |
| `user_key_file` | string | cond.* | -- | Path to user key file |
| `token` | secret | cond.* | -- | Pushover application token (inline) |
| `token_file` | string | cond.* | -- | Path to application token file |
| `title` | string | no | `'{{ template "pushover.default.title" . }}'` | Notification title |
| `message` | string | no | `'{{ template "pushover.default.message" . }}'` | Notification body |
| `url` | string | no | `'{{ template "pushover.default.url" . }}'` | Supplementary URL |
| `url_title` | string | no | -- | Title for supplementary URL |
| `device` | string | no | -- | Specific device name to target |
| `sound` | string | no | -- | Notification sound name |
| `priority` | string | no | (dynamic) | Priority: `-2`..`2`; emergency requires retry/expire |
| `retry` | duration | no | 1m | Retry interval for emergency priority (required when priority=2) |
| `expire` | duration | no | 1h | How long to keep retrying emergency (required when priority=2) |
| `ttl` | duration | no | -- | Notification TTL on Pushover server |
| `html` | bool | no | false | Render message as HTML |
| `monospace` | bool | no | false | Use monospace font |

### Validation Rules

- `user_key` or `user_key_file` is required.
- `token` or `token_file` is required.
- At most one of each paired inline/file variant.
- `html` and `monospace` are mutually exclusive.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: pushover-alerts
    pushover_configs:
      - user_key_file: /etc/alertmanager/pushover-user-key
        token_file: /etc/alertmanager/pushover-token
        priority: '{{ if eq .Status "firing" }}2{{ else }}0{{ end }}'
        retry: 1m
        expire: 1h
        sound: siren
        html: true
```

---

## wechat_configs

Sends notifications via WeChat Work (WeCom).

```yaml
receivers:
  - name: example-wechat
    wechat_configs:
      - corp_id: <corp-id>
        api_secret: <secret>
        agent_id: <agent-id>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | URL | no | global `wechat_api_url` | WeChat Work API base URL |
| `api_secret` | secret | cond.* | global `wechat_api_secret` | API secret (inline) |
| `api_secret_file` | string | cond.* | global `wechat_api_secret_file` | Path to API secret file |
| `corp_id` | string | yes* | global `wechat_api_corp_id` | WeChat Work Corp ID |
| `message` | string | no | `'{{ template "wechat.default.message" . }}'` | Message body template |
| `to_user` | string | no | `'{{ template "wechat.default.to_user" . }}'` | Target user IDs (comma-separated, `@all` for all) |
| `to_party` | string | no | `'{{ template "wechat.default.to_party" . }}'` | Target party IDs (comma-separated) |
| `to_tag` | string | no | `'{{ template "wechat.default.to_tag" . }}'` | Target tag IDs (comma-separated) |
| `agent_id` | string | no | `'{{ template "wechat.default.agent_id" . }}'` | Application agent ID |
| `message_type` | string | no | `"text"` | Message format: `"text"` or `"markdown"` |

\* Required globally or locally.

### Validation Rules

- At most one of `api_secret` / `api_secret_file`.
- `message_type` must be `"text"` or `"markdown"`.
- Default `send_resolved`: `false`.

### Complete Example

```yaml
receivers:
  - name: wechat-work
    wechat_configs:
      - corp_id: ww1234567890abcdef
        api_secret_file: /etc/alertmanager/wechat-secret
        agent_id: "1000002"
        to_user: "@all"
        message_type: markdown
        message: '**Alert:** {{ .CommonLabels.alertname }}\n**Severity:** {{ .CommonLabels.severity }}'
```

---

## sns_configs

Sends notifications via AWS SNS.

```yaml
receivers:
  - name: example-sns
    sns_configs:
      - topic_arn: arn:aws:sns:...
      - sigv4:
          region: us-east-1
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | string | no | -- | SNS API endpoint URL override |
| `sigv4` | SigV4Config | no | -- | AWS Signature V4 configuration |
| `topic_arn` | string | cond.* | -- | SNS topic ARN |
| `phone_number` | string | cond.* | -- | SMS phone number (E.164 format) |
| `target_arn` | string | cond.* | -- | SNS target ARN |
| `subject` | string | no | `'{{ template "sns.default.subject" . }}'` | Message subject |
| `message` | string | no | `'{{ template "sns.default.message" . }}'` | Message body |
| `attributes` | map[string]string | no | -- | SNS message attributes |
| `use_aws_http_client` | bool | no | false | Use AWS SDK's built-in HTTP client instead of Alertmanager's |

\* Exactly one of `topic_arn`, `phone_number`, or `target_arn` is required.

### SigV4Config Sub-fields

AWS Signature V4 authentication configuration:

| Field | Type | Description |
| --- | --- | --- |
| `region` | string | AWS region |
| `access_key` | string | AWS access key ID |
| `secret_key` | string | AWS secret access key |
| `profile` | string | AWS credential profile name |
| `role_arn` | string | ARN of role to assume |
| `filename` | string | Path to credentials file |

### Validation Rules

- Exactly one of `topic_arn`, `phone_number`, or `target_arn` must be provided.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: sns-alerts
    sns_configs:
      - topic_arn: arn:aws:sns:us-east-1:123456789012:alerts
        sigv4:
          region: us-east-1
          role_arn: arn:aws:iam::123456789012:role/AlertmanagerRole
        subject: '[{{ .Status }}] {{ .CommonLabels.alertname }}'
        attributes:
          severity: "{{ .CommonLabels.severity }}"
```

---

## telegram_configs

Sends notifications via Telegram Bot API.

```yaml
receivers:
  - name: example-telegram
    telegram_configs:
      - bot_token: <token>
        chat_id: <id>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | URL | **yes** | global `telegram_api_url` | Telegram Bot API base URL |
| `bot_token` | secret | cond.* | global `telegram_bot_token` | Bot token (inline) |
| `bot_token_file` | string | cond.* | global `telegram_bot_token_file` | Path to bot token file |
| `chat_id` | int64 | cond.* | -- | Target chat ID (integer) |
| `chat_id_file` | string | cond.* | -- | Path to file containing chat ID |
| `message_thread_id` | int | no | -- | Topic/thread ID (for forum supergroups) |
| `message` | string | no | `'{{ template "telegram.default.message" . }}'` | Message body |
| `disable_notifications` | bool | no | false | Send silently (no sound/notification) |
| `parse_mode` | string | no | `"HTML"` | Parse mode: `"HTML"`, `"Markdown"`, `"MarkdownV2"`, or empty |

\* `bot_token`/`bot_token_file` required globally or locally. `chat_id`/`chat_id_file` always required.

### Validation Rules

- At most one of `bot_token` / `bot_token_file`.
- At least one of `chat_id` / `chat_id_file` is required.
- At most one of `chat_id` / `chat_id_file`.
- `parse_mode` must be empty, `"HTML"`, `"Markdown"`, or `"MarkdownV2"`.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: telegram-ops
    telegram_configs:
      - bot_token_file: /etc/alertmanager/telegram-token
        chat_id: -1001234567890
        message_thread_id: 42
        message: '{{ template "telegram.default.message" . }}'
        parse_mode: HTML
        disable_notifications: false
```

---

## discord_configs

Sends notifications via Discord webhooks.

```yaml
receivers:
  - name: example-discord
    discord_configs:
      - webhook_url: https://discord.com/api/webhooks/xxx/yyy
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `webhook_url` | SecretURL | cond.* | -- | Discord webhook URL (inline) |
| `webhook_url_file` | string | cond.* | -- | Path to file containing webhook URL |
| `content` | string | no | -- | Raw message content (overrides embedding) |
| `title` | string | no | `'{{ template "discord.default.title" . }}'` | Embed title |
| `message` | string | no | `'{{ template "discord.default.message" . }}'` | Embed description/body |
| `username` | string | no | -- | Override webhook username |
| `avatar_url` | string | no | -- | Override webhook avatar URL |

\* Exactly one of `webhook_url` or `webhook_url_file` is required.

### Validation Rules

- Exactly one of `webhook_url` / `webhook_url_file` must be provided.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: discord-alerts
    discord_configs:
      - webhook_url: https://discord.com/api/webhooks/xxx/yyy
        title: '{{ template "discord.default.title" . }}'
        message: '{{ template "discord.default.message" . }}'
        username: "Alertmanager"
```

---

## msteams_configs

Sends notifications to Microsoft Teams via incoming webhook connector (legacy Office 365 Connector format).

```yaml
receivers:
  - name: example-msteams
    msteams_configs:
      - webhook_url: https://outlook.office.com/webhook/...
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `webhook_url` | SecretURL | cond.* | -- | Teams incoming webhook URL (inline) |
| `webhook_url_file` | string | cond.* | -- | Path to file containing webhook URL |
| `title` | string | no | `'{{ template "msteams.default.title" . }}'` | Card title |
| `summary` | string | no | `'{{ template "msteams.default.summary" . }}'` | Card summary (popup preview) |
| `text` | string | no | `'{{ template "msteams.default.text" . }}'` | Card body text |

\* Exactly one of `webhook_url` or `webhook_url_file` is required.

### Validation Rules

- Exactly one of `webhook_url` / `webhook_url_file` must be provided.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: teams-alerts
    msteams_configs:
      - webhook_url: https://outlook.office.com/webhook/xxx/IncomingWebhook/yyy
        title: '{{ template "msteams.default.title" . }}'
        summary: '{{ .CommonLabels.alertname }} - {{ .CommonLabels.severity }}'
        text: '{{ template "msteams.default.text" . }}'
```

---

## msteamsv2_configs

Sends notifications to Microsoft Teams via workflow bot (Adaptive Card format, newer).

```yaml
receivers:
  - name: example-msteamsv2
    msteamsv2_configs:
      - webhook_url: https://<tenant>.office.com/webhookbot/...
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `webhook_url` | SecretURL | cond.* | -- | Teams workflow bot webhook URL (inline) |
| `webhook_url_file` | string | cond.* | -- | Path to file containing webhook URL |
| `title` | string | no | `'{{ template "msteamsv2.default.title" . }}'` | Card title |
| `text` | string | no | `'{{ template "msteamsv2.default.text" . }}'` | Card body text |

\* Exactly one of `webhook_url` or `webhook_url_file` is required.

### Validation Rules

- Exactly one of `webhook_url` / `webhook_url_file` must be provided.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: teams-v2-alerts
    msteamsv2_configs:
      - webhook_url: https://mytenant.office.com/webhookbot/xxx
        title: '{{ template "msteamsv2.default.title" . }}'
        text: '{{ template "msteamsv2.default.text" . }}'
```

---

## jira_configs

Creates Jira issues from alerts (Jira Cloud or Data Center).

```yaml
receivers:
  - name: example-jira
    jira_configs:
      - project: OPS
        issue_type: Bug
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | URL | yes* | global `jira_api_url` | Jira Cloud/Data Center API base URL |
| `api_type` | string | no | `"auto"` | API type: `"auto"`, `"cloud"`, or `"datacenter"` |
| `project` | string | **yes** | -- | Jira project key |
| `summary` | JiraFieldConfig | no | (template) | Issue summary field |
| `description` | JiraFieldConfig | no | (template) | Issue description field |
| `labels` | list of string | no | -- | Labels to apply |
| `priority` | string | no | (template) | Priority field value |
| `issue_type` | string | **yes** | -- | Issue type name |
| `reopen_transition` | string | no | -- | Transition name for reopening resolved issues |
| `resolve_transition` | string | no | -- | Transition name for resolving open issues |
| `wont_fix_resolution` | string | no | -- | Resolution name for wont-fix |
| `reopen_duration` | duration | no | -- | Duration after which resolved issues can be reopened |
| `fields` | map[string]any | no | -- | Custom/additional Jira fields |

\* Required globally or locally.

### JiraFieldConfig Sub-fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `template` | string | -- | Go template string for rendering the field value |
| `enable_update` | bool* | true | Whether to include this field when updating existing issues |

\* Omitting `enable_update` defaults to `true`. Set to `false` to skip updates.

The `summary` and `description` fields also accept a plain string (backward compatible), which is treated as a template with `enable_update` defaulting to `false`.

### Validation Rules

- `project` is always required.
- `issue_type` is always required.
- `api_type` must be `"auto"`, `"cloud"`, or `"datacenter"`.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: jira-incidents
    jira_configs:
      - api_url: https://your-org.atlassian.net
        api_type: cloud
        project: OPS
        issue_type: Incident
        summary:
          template: '[{{ .Status }}] {{ .CommonLabels.alertname }}'
          enable_update: false
        description:
          template: '{{ template "jira.default.description" . }}'
          enable_update: true
        priority: High
        labels:
          - prometheus
          - auto-created
        reopen_transition: "Reopen Issue"
        resolve_transition: "Resolve Issue"
        reopen_duration: 72h
        fields:
          components:
            - name: Platform
```

---

## rocketchat_configs

Sends notifications to Rocket.Chat.

```yaml
receivers:
  - name: example-rocketchat
    rocketchat_configs:
      - channel: "#alerts"
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | URL | no | global `rocketchat_api_url` | Rocket.Chat API base URL |
| `token_id` | secret | cond.* | global `rocketchat_token_id` | Token ID (inline) |
| `token_id_file` | string | cond.* | global `rocketchat_token_id_file` | Path to token ID file |
| `token` | secret | cond.* | global `rocketchat_token` | Auth token (inline) |
| `token_file` | string | cond.* | global `rocketchat_token_file` | Path to auth token file |
| `channel` | string | no | -- | Channel override (`#channel` or `@user`) |
| `color` | string | no | (dynamic) | Attachment color |
| `title` | string | no | `'{{ template "rocketchat.default.title" . }}'` | Attachment title |
| `title_link` | string | no | `'{{ template "rocketchat.default.titlelink" . }}'` | Attachment title link |
| `text` | string | no | `'{{ template "rocketchat.default.text" . }}'` | Attachment body text |
| `fields` | list of RocketchatField | no | -- | Attachment fields |
| `short_fields` | bool | no | false | Render all fields as short |
| `emoji` | string | no | `'{{ template "rocketchat.default.emoji" . }}'` | Bot emoji |
| `icon_url` | string | no | `'{{ template "rocketchat.default.iconurl" . }}'` | Bot icon URL |
| `image_url` | string | no | -- | Attachment image URL |
| `thumb_url` | string | no | -- | Attachment thumbnail URL |
| `link_names` | bool | no | false | Find and link usernames |
| `actions` | list of RocketchatAction | no | -- | Interactive action buttons |

\* Token ID and token are required globally or locally.

### RocketchatAttachmentField Sub-fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `title` | string | **yes** | -- | Field title |
| `value` | string | **yes** | -- | Field value |
| `short` | bool | no | -- | Display inline |

### RocketchatAttachmentAction Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `type` | string | Action type |
| `text` | string | Button/element text |
| `url` | string | URL for link buttons |
| `image_url` | string | Image URL for image buttons |
| `is_webview` | bool | Open as webview |
| `webview_height_ratio` | string | Webview height ratio |
| `msg` | string | Message sent on interaction |
| `msg_in_chat_window` | bool | Post message to chat window |
| `msg_processing_type` | string | Message processing type |

### Validation Rules

- At most one of `token` / `token_file`.
- At most one of `token_id` / `token_id_file`.
- Default `send_resolved`: `false`.

### Complete Example

```yaml
receivers:
  - name: rocketchat-alerts
    rocketchat_configs:
      - channel: "#alerts"
        title: '{{ template "rocketchat.default.title" . }}'
        text: '{{ template "rocketchat.default.text" . }}'
        color: '{{ if eq .Status "firing" }}red{{ else }}green{{ end }}'
        fields:
          - title: Severity
            value: "{{ .CommonLabels.severity }}"
            short: true
```

---

## mattermost_configs

Sends notifications to Mattermost via incoming webhooks.

```yaml
receivers:
  - name: example-mattermost
    mattermost_configs:
      - webhook_url: https://mattermost.example.com/hooks/xxx
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `webhook_url` | SecretURL | cond.* | global `mattermost_webhook_url` | Mattermost webhook URL (inline) |
| `webhook_url_file` | string | cond.* | global `mattermost_webhook_url_file` | Path to webhook URL file |
| `channel` | string | no | -- | Channel override |
| `username` | string | no | `'{{ template "mattermost.default.username" . }}'` | Bot username |
| `text` | string | no | `'{{ template "mattermost.default.text" . }}'` | Message body |
| `fallback` | string | no | `'{{ template "mattermost.default.fallback" . }}'` | Fallback text (notifications off) |
| `color` | string | no | `'{{ template "mattermost.default.color" . }}'` | Attachment color |
| `pretext` | string | no | -- | Attachment pretext |
| `author_name` | string | no | -- | Attachment author name |
| `author_link` | string | no | -- | Attachment author link |
| `author_icon` | string | no | -- | Attachment author icon URL |
| `title` | string | no | `'{{ template "mattermost.default.title" . }}'` | Attachment title |
| `title_link` | string | no | `'{{ template "mattermost.default.titlelink" . }}'` | Attachment title link |
| `fields` | list of MattermostField | no | -- | Attachment fields |
| `thumb_url` | string | no | -- | Attachment thumbnail URL |
| `footer` | string | no | -- | Attachment footer text |
| `footer_icon` | string | no | -- | Attachment footer icon URL |
| `image_url` | string | no | -- | Attachment image URL |
| `icon_url` | string | no | -- | Bot icon URL |
| `icon_emoji` | string | no | -- | Bot icon emoji |
| `attachments` | list of MattermostAttachment | no | -- | Additional attachments |
| `type` | string | no | -- | Attachment type override |
| `props` | MattermostProps | no | -- | Additional properties (only `card` takes effect) |
| `priority` | MattermostPriority | no | -- | Notification priority settings |

\* Required globally or locally.

### MattermostField Sub-fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | string | **yes** | Field title |
| `value` | string | **yes** | Field value |
| `short` | bool | no | Display inline |

### MattermostAttachment Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `fallback` | string | Fallback text |
| `color` | string | Sidebar color |
| `pretext` | string | Pretext above body |
| `text` | string | Body text |
| `author_name` | string | Author name |
| `author_link` | string | Author link |
| `author_icon` | string | Author icon URL |
| `title` | string | Title |
| `title_link` | string | Title link |
| `fields` | list of MattermostField | Fields |
| `thumb_url` | string | Thumbnail URL |
| `footer` | string | Footer text |
| `footer_icon` | string | Footer icon URL |
| `image_url` | string | Image URL |

### MattermostProps Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `card` | string | Card JSON content |

### MattermostPriority Sub-fields

| Field | Type | Description |
| --- | --- | --- |
| `priority` | string | Priority level |
| `requested_ack` | bool | Request acknowledgment |
| `persistent_notifications` | bool | Persistent notification flag |

### Validation Rules

- At most one of `webhook_url` / `webhook_url_file`.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: mattermost-team
    mattermost_configs:
      - webhook_url: https://mattermost.example.com/hooks/xxx
        channel: "~town-square"
        title: '{{ template "mattermost.default.title" . }}'
        text: '{{ template "mattermost.default.text" . }}'
        priority:
          priority: urgent
          requested_ack: true
```

---

## webex_configs

Sends notifications via Cisco Webex Messages API.

```yaml
receivers:
  - name: example-webex
    webex_configs:
      - room_id: <room-id>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `api_url` | URL | yes* | global `webex_api_url` | Webex Messages API URL |
| `message` | string | no | `'{{ template "webex.default.message" . }}'` | Message body |
| `room_id` | string | **yes** | -- | Target room ID |

\* Required globally or locally.

### Validation Rules

- `room_id` is always required.
- `http_config.authorization` (Bearer token) is required unless inherited from global.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: webex-alerts
    webex_configs:
      - room_id: "Y2lzY29zcGFyazovL3Vz..."
        message: '{{ template "webex.default.message" . }}'
        http_config:
          authorization:
            type: Bearer
            credentials_file: /etc/alertmanager/webex-token
```

---

## incidentio_configs

Creates incidents in incident.io.

```yaml
receivers:
  - name: example-incidentio
    incidentio_configs:
      - url: https://api.incident.io/...
        alert_source_token: <token>
```

### Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `http_config` | http_config | no | global `http_config` | HTTP client configuration |
| `url` | URL | cond.* | -- | incident.io API endpoint URL (inline) |
| `url_file` | string | cond.* | -- | Path to file containing API URL |
| `alert_source_token` | secret | cond.* | -- | Authentication token (inline) |
| `alert_source_token_file` | string | cond.* | -- | Path to authentication token file |
| `max_alerts` | uint64 | no | 0 (unlimited) | Maximum alerts per incident (0 = unlimited) |
| `timeout` | duration | no | -- | Max time for API call (0 = no limit) |

\* Exactly one of `url` / `url_file` is required. Auth via `alert_source_token`/*_file` or `http_config.authorization` is required.

### Validation Rules

- Exactly one of `url` / `url_file`.
- At most one of `alert_source_token` / `alert_source_token_file`.
- Cannot use `alert_source_token` alongside `http_config.authorization`.
- At least one auth method must be configured.
- Default `send_resolved`: `true`.

### Complete Example

```yaml
receivers:
  - name: incidentio-incidents
    incidentio_configs:
      - url: https://api.incident.io/v2/alerts
        alert_source_token_file: /etc/alertmanager/incidentio-token
        max_alerts: 50
        timeout: 30s
```
