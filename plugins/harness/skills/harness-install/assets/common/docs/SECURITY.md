# Security

## Purpose

SECURITY.md captures the durable security contract: what the team protects, who is allowed to do what, where secrets live, and where audit trails for sensitive actions land. An agent loading this file should be able to refuse a request that would cross a documented boundary without escalating to a human.

## Threat Model

- Spoofing: stolen session cookie replays end-user identity → short session TTL (15 min), refresh-token rotation (7-day lifetime), optional IP + user-agent fingerprint binding.
- Tampering: request body altered in transit → enforce TLS only (no HTTP), sign request bodies for webhooks, schema-validate every boundary payload.
- Repudiation: user denies an action → immutable audit log with monotonic event IDs, write to append-only sink with no delete permission.
- Information disclosure: PII leaked in logs → structured logger drops fields not in `PII_ALLOWLIST` before write; assert coverage in unit tests.
- Denial of service: traffic spike or expensive query exhausts the service → per-route rate limit (token bucket), query timeout (30s default), circuit breaker on downstream calls.
- Elevation of privilege: low-privilege user reaches admin path → RBAC checked at handler entry; tests cover deny-case per route.

## Secret Management

- Database credentials live in {{secret-manager}} and are injected via environment variables at boot; rotate every 90 days.
- Third-party API keys live in {{secret-manager}} under per-service paths; rotate every 90 days or immediately on compromise.
- Signing keys for outbound webhooks live in {{secret-manager}}/signing/; rotate every 180 days with overlapping validity window.
- Never write secrets into the repository; pre-commit hook (`docs/harness/git-hooks/pre-commit`) blocks committed secret patterns.

## Permission Boundaries

- End user: may read and modify their own resources; MUST NOT read other users' data.
- Support agent: may read across users via impersonation API with audit log entry; MUST NOT modify billing data.
- Admin: may modify configuration, feature flags, and per-tenant settings; MUST NOT execute arbitrary SQL.
- Automation / agent: may run pre-approved workflow steps with a scoped service-account token; MUST NOT escalate to admin scope without human approval.

## Audit Logging

- Authentication events (login, logout, token refresh): log to {{audit-sink}}, schema audit.auth.v1, retention 365 days.
- Authorization decisions (deny only): log to {{audit-sink}}, schema audit.authz.v1, retention 365 days.
- Admin configuration changes: log to {{audit-sink}}, schema audit.config.v1, retention 7 years.
- Data export / impersonation: log to {{audit-sink}}, schema audit.export.v1, retention 7 years.

## When To Update

- When a new threat is identified.
- When a new secret is introduced or rotated.
- When a permission boundary changes.
- When a new sensitive action is added or its audit destination changes.

## Required Evidence

- Cite the threat-model decision record under `docs/design-docs/`.
- Link to the secret-rotation runbook.
- Link to the IAM policy or RBAC config that enforces each permission boundary.
- Link to the audit-log schema or dashboard.
