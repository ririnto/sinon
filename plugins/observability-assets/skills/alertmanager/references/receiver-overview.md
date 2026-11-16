# Receiver Overview

## Receivers Overview

A receiver is a named destination that sends notifications through one or more configured channel blocks.
Every receiver MUST have a unique `name`, referenced by `receiver:` in route blocks.
A receiver with no notification configs is valid (acts as a null sink).

Common channel blocks: `email_configs`, `slack_configs`, `pagerduty_configs`, `webhook_configs`, `opsgenie_configs`, and `telegram_configs`.
Alertmanager supports 18 receiver types in total, including SNS, Discord, both MS Teams variants, Jira, Rocket.Chat, Mattermost, Webex, VictorOps, Pushover, WeChat, and incident.io.
Default `send_resolved` is `false` for email, Slack, WeChat, and Rocket.Chat, and `true` for all other receiver types.
For complete field schemas for every receiver type, see [`./receiver-types.md`](./receiver-types.md).
