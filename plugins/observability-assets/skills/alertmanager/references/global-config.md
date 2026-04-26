---
title: "Alertmanager Global Configuration"
description: "Open this when you need the complete global configuration reference, including all fields, defaults, credential pairing rules, and interaction with receiver-level overrides."
---

## Alertmanager Global Configuration

Use this reference when you need the full global configuration schema, field-by-field details on how global defaults interact with receiver-level overrides, and the validation rules for paired credential fields.

## Purpose

The `global:` block defines default values that every receiver inherits. Individual receivers can override most global settings locally. This design keeps receiver configs minimal while allowing per-receiver customization where needed.

## Complete Field Reference

### Timing and Resolution

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `resolve_timeout` | duration | `5m` | Time after which an alert is declared resolved if it has not been updated. An alert that has not changed for longer than this duration is considered resolved by Alertmanager. |

### SMTP / Email Defaults

These fields set defaults inherited by all `email_configs` entries.

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `smtp_smarthost` | host:port | -- | SMTP server address in `host:port` format. Required if any email_configs exist without local `smarthost`. |
| `smtp_from` | string | -- | Sender email address. Required if any email_configs exist without local `from`. |
| `smtp_hello` | string | `"localhost"` | HELO/EHLO hostname sent to the SMTP server during connection setup. |
| `smtp_auth_username` | string | -- | Username for SMTP authentication (PLAIN, LOGIN, etc.). |
| `smtp_auth_password` | secret | -- | Password for SMTP authentication (inline value). |
| `smtp_auth_password_file` | string | -- | Path to a file containing the SMTP auth password. |
| `smtp_auth_secret` | secret | -- | Secret for CRAM-MD5 or similar challenge-response auth mechanisms. |
| `smtp_auth_secret_file` | string | -- | Path to a file containing the SMTP auth secret. |
| `smtp_auth_identity` | string | -- | Identity string for SMTP authentication (used with certain auth mechanisms). |
| `smtp_require_tls` | bool | `true` | Whether TLS is required for the SMTP connection. Receiver-level `require_tls` is a pointer; omitting it inherits this global default. |
| `smtp_tls_config` | tls_config | -- | TLS configuration for SMTP connections. Merged with receiver-level settings if provided locally. |
| `smtp_force_implicit_tls` | bool* | nil | Controls implicit vs explicit TLS. `true` = force implicit TLS (direct TLS, port 465). `false` = force explicit TLS/STARTTLS. `nil` = auto-detect based on port number (465 = implicit, others = explicit). |

\* Pointer type: nil means auto-detect.

### Slack Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `slack_api_url` | URL | -- | Slack webhook API URL. Used when configuring Slack via webhook-based approach (not app token). |
| `slack_api_url_file` | string | -- | Path to a file containing the Slack API URL. |
| `slack_app_token` | secret | -- | Slack bot/app OAuth token for chat.postMessage API usage. |
| `slack_app_token_file` | string | -- | Path to a file containing the Slack app token. |
| `slack_app_url` | URL | `https://slack.com/api/chat.postMessage` | Base URL for Slack app API calls. Rarely needs changing. |

### PagerDuty Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `pagerduty_url` | URL | `https://events.pagerduty.com/v2/enqueue` | PagerDuty Events API v2 endpoint URL. |

### OpsGenie Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `opsgenie_api_url` | URL | `https://api.opsgenie.com/` | OpsGenie API base URL. A trailing `/` is appended automatically if missing. |
| `opsgenie_api_key` | secret | -- | OpsGenie API key (inline). |
| `opsgenie_api_key_file` | string | -- | Path to a file containing the OpsGenie API key. |

### WeChat Work Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `wechat_api_url` | URL | `https://qyapi.weixin.qq.com/cgi-bin/` | WeChat Work (WeCom) API base URL. A trailing `/` is appended automatically if missing. |
| `wechat_api_secret` | secret | -- | WeChat Work application API secret (inline). |
| `wechat_api_secret_file` | string | -- | Path to a file containing the WeChat API secret. |
| `wechat_api_corp_id` | string | -- | WeChat Work enterprise Corp ID. |

### VictorOps / Splunk On-Call Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `victorops_api_url` | URL | `https://alert.victorops.com/integrations/generic/20131114/alert/` | VictorOps integration API endpoint URL. A trailing `/` is appended automatically if missing. |
| `victorops_api_key` | secret | -- | VictorOps API key (inline). |
| `victorops_api_key_file` | string | -- | Path to a file containing the VictorOps API key. |

### Telegram Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `telegram_api_url` | URL | `https://api.telegram.org` | Telegram Bot API base URL. |
| `telegram_bot_token` | secret | -- | Telegram bot authentication token (inline). |
| `telegram_bot_token_file` | string | -- | Path to a file containing the Telegram bot token. |

### Webex Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `webex_api_url` | URL | `https://webexapis.com/v1/messages` | Cisco Webex Messages API endpoint URL. |

### Rocket.Chat Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `rocketchat_api_url` | URL | `https://open.rocket.chat/` | Rocket.Chat server API base URL. |
| `rocketchat_token` | secret | -- | Rocket.Chat personal access token or auth token (inline). |
| `rocketchat_token_file` | string | -- | Path to a file containing the Rocket.Chat token. |
| `rocketchat_token_id` | secret | -- | Rocket.Chat token ID (inline). |
| `rocketchat_token_id_file` | string | -- | Path to a file containing the Rocket.Chat token ID. |

### Mattermost Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `mattermost_webhook_url` | URL | -- | Mattermost incoming webhook URL. |
| `mattermost_webhook_url_file` | string | -- | Path to a file containing the Mattermost webhook URL. |

### Jira Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `jira_api_url` | URL | -- | Jira Cloud or Data Center API base URL. |

### HTTP Client Defaults

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `http_config` | http_config | (prometheus common defaults) | Default HTTP client configuration applied to all receivers that support `http_config`. Includes timeout, TLS, proxy, basic auth, bearer token, and OAuth2 settings. |

For the complete `http_config` schema, see [`./shared-types.md`](./shared-types.md).

## Credential Pairing Rules

Many global fields come in inline/file pairs. These rules apply universally:

1. **At most one** of each pair may be configured. Configuring both causes config load failure.
2. The `*_file` variant reads the value from a filesystem path at config load time.
3. If neither variant is set globally, receivers must provide their own value.
4. If only the global inline value is set, receivers inherit it unless they override locally.
5. If only the global `*_file` variant is set, receivers inherit the resolved value unless they override locally.

Paired fields:

| Inline Field | File Field | Receiver Types Using It |
| --- | --- | --- |
| `smtp_auth_password` | `smtp_auth_password_file` | email_configs |
| `smtp_auth_secret` | `smtp_auth_secret_file` | email_configs |
| `opsgenie_api_key` | `opsgenie_api_key_file` | opsgenie_configs |
| `victorops_api_key` | `victorops_api_key_file` | victorops_configs |
| `telegram_bot_token` | `telegram_bot_token_file` | telegram_configs |
| `wechat_api_secret` | `wechat_api_secret_file` | wechat_configs |
| `slack_app_token` | `slack_app_token_file` | slack_configs |
| `slack_api_url` | `slack_api_url_file` | slack_configs |
| `rocketchat_token` | `rocketchat_token_file` | rocketchat_configs |
| `rocketchat_token_id` | `rocketchat_token_id_file` | rocketchat_configs |
| `mattermost_webhook_url` | `mattermost_webhook_url_file` | mattermost_configs |

Additionally, these URL fields have `_file` variants:

| Inline URL Field | File URL Field | Receiver Type |
| --- | --- | --- |
| N/A (receiver-level) | `url_file` | webhook_configs, incidentio_configs |
| N/A (receiver-level) | `webhook_url_file` | discord_configs, msteams_configs, msteamsv2_configs, mattermost_configs |
| N/A (receiver-level) | `api_url_file` | slack_configs |
| N/A (receiver-level) | `bot_token_file` | telegram_configs |
| N/A (receiver-level) | `service_key_file`, `routing_key_file` | pagerduty_configs |
| N/A (receiver-level) | `api_key_file` | opsgenie_configs |
| N/A (receiver-level) | `api_key_file`, `api_secret_file` | victorops_configs, wechat_configs |
| N/A (receiver-level) | `user_key_file`, `token_file` | pushover_configs |
| N/A (receiver-level) | `token_file`, `token_id_file` | rocketchat_configs |
| N/A (receiver-level) | `chat_id_file` | telegram_configs |
| N/A (receiver-level) | `alert_source_token_file` | incidentio_configs |

## Mutual Exclusion Rules

Beyond pairing rules, some global fields are mutually exclusive:

- **Slack**: Cannot configure both `slack_app_token`/`slack_app_token_file` AND `slack_api_url`/`slack_api_url_file` unless `slack_api_url` equals the default `slack_app_url` (the legacy workaround path).
- **Slack**: Cannot configure both `slack_app_token` AND `slack_app_token_file`.

## Inheritance Behavior

When a receiver does not specify a field locally, the global default is used. The inheritance works as follows at config-load time:

- For scalar values (`string`, `bool`, `duration`): the global value is copied into the receiver's resolved config.
- For pointer values (`*bool`, `*tls_config`): nil means "inherit from global"; non-nil means "override".
- For struct values (`http_config`): the global config is used as baseline; receiver-level `http_config` replaces it entirely (not merged).
- For secret values: the resolved secret string from global is used.

This means setting `http_config` in a receiver completely replaces the global `http_config` for that receiver -- it is not a partial merge.
