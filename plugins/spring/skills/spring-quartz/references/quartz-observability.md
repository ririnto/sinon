---
title: Quartz Observability Reference
description: >-
  Reference for logging, health, and metrics around Quartz scheduler state and execution.
---

Use this reference when Quartz job lifecycle control is already implemented and the remaining blocker is operational visibility. Canonical health and metrics templates live in `SKILL.md`.

## Logging Rule

Log job registration, pause/resume, deletion, and failure with `JobKey` or `TriggerKey`. Keep logs aligned with operator actions so runtime changes are auditable:

```java
logger.info("Job registered: group={}, name={}", jobKey.getGroup(), jobKey.getName());
logger.warn("Job paused: group={}, name={}", jobKey.getGroup(), jobKey.getName());
```

Key points:

- include `JobKey` and `TriggerKey` in every log so entries are traceable to specific scheduler objects
- log failure outcomes with the full execution context including `JobExecutionContext` error details when available
- align log level with operator intent: registration and deletion are info, pauses are warn, failures are error
