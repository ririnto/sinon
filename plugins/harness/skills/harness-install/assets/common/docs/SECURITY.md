# Security

## Purpose

`SECURITY.md` captures the durable security contract.
It states what the team protects, who is allowed to do what, where secrets live, and where audit trails for sensitive actions land.
An agent loading this file should be able to refuse a request that would cross a documented boundary without escalating to a human.

## Threat Model

- Spoofing: stolen session cookie replays end-user identity.
  - Use a short session TTL: `{{short session TTL}}`.
  - Rotate refresh tokens with a `{{refresh token lifetime}}`.
  - Optionally bind sessions to IP and user-agent fingerprint.
- Tampering: request body altered in transit.
  - Enforce TLS only.
  - Sign request bodies for webhooks.
  - Schema-validate every boundary payload.
- Repudiation: user denies an action.
  - Use immutable audit log with monotonic event IDs.
  - Write to append-only sink with no delete permission.
- Information disclosure: PII leaked in logs.
  - Drop fields not in `PII_ALLOWLIST` before write.
  - Assert coverage in unit tests.
- Denial of service: traffic spike or expensive query exhausts the service.
  - Use per-route token-bucket rate limits.
  - Apply a 30-second default query timeout.
  - Add circuit breakers on downstream calls.
- Elevation of privilege: low-privilege user reaches admin path.
  - Check RBAC at handler entry.
  - Cover deny-case behavior per route in tests.

## Secret Management

- Database credentials live in the configured secret manager and are injected via environment variables at boot.
  - Rotate on a regular cadence (for example every 90 days).
- Third-party API keys live in the configured secret manager under per-service paths.
  - Rotate on a regular cadence (for example every 90 days) or immediately on compromise.
- Signing keys for outbound webhooks live in the configured secret manager/signing/.
  - Rotate with an overlapping validity window (for example every 180 days).
- Never write secrets into the repository.
  - Add a secret scanner or CI policy when the project needs mechanical secret-pattern enforcement.

## Permission Boundaries

- End user: may read and modify their own resources.
  - MUST NOT read other users' data.
- Support agent: may read across users via impersonation API with audit log entry.
  - MUST NOT modify billing data.
- Admin: may modify configuration, feature flags, and per-tenant settings.
  - MUST NOT execute arbitrary SQL.
- Automation / agent: may run pre-approved workflow steps with a scoped service-account token.
  - MUST NOT escalate to admin scope without human approval.

## Audit Logging

- Authentication events (login, logout, token refresh): log to the audit log sink, schema audit.auth.v1, retention 365 days.
- Authorization decisions (deny only): log to the audit log sink, schema audit.authz.v1, retention 365 days.
- Admin configuration changes: log to the audit log sink, schema audit.config.v1, retention 7 years.
- Data export / impersonation: log to the audit log sink, schema audit.export.v1, retention 7 years.

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
