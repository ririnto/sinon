# Security

## Purpose

SECURITY.md captures the durable security contract: what the team protects, who is allowed to do what, where secrets live, and where audit trails for sensitive actions land. An agent loading this file should be able to refuse a request that would cross a documented boundary without escalating to a human.

## Threat Model

- Spoofing: Attacker impersonates a legitimate user or service to gain unauthorized access. Mitigation: enforce strict authentication and identity verification on all endpoints.
- Tampering: Attacker modifies data in transit or at rest without authorization. Mitigation: use encrypted channels, cryptographic signatures, and immutable audit logs.
- Repudiation: Actor denies performing a sensitive action. Mitigation: maintain tamper-proof audit logs with cryptographic signatures and timestamp proof.
- Information disclosure: Attacker gains unauthorized access to secrets, private data, or sensitive logs. Mitigation: encrypt secrets at rest, enforce least-privilege access, and restrict log visibility.
- Denial of service: Attacker overloads or crashes services to disrupt availability. Mitigation: implement rate limiting, circuit breakers, and redundancy for critical systems.
- Elevation of privilege: Attacker gains higher permissions than authorized. Mitigation: enforce role-based access control, implement least-privilege defaults, and audit permission changes.

## Secret Management

- Database credentials: stored in secret manager, rotated quarterly; accessed by backend services only via credentials API.
- Third-party API keys: stored in secret manager, rotated annually or after any compromise; never logged or exposed in error messages.

## Permission Boundaries

- End user: may view and modify their own data; MUST NOT access other users' data or modify system configuration.
- Support agent: may view user data and moderate content; MUST NOT modify authentication settings or access production databases directly.
- Admin: may modify system configuration, manage users, and access logs; MUST NOT bypass audit logging or disable security controls.
- Automation/agent: may execute delegated tasks within assigned scope; MUST NOT escalate privileges or access data outside its declared boundaries.

## Audit Logging

- Authentication success/failure: log to centralized audit system, schema `audit.auth`, retention 365 days.
- Data access by support agents: log to centralized audit system, schema `audit.data_access`, retention 90 days.

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
