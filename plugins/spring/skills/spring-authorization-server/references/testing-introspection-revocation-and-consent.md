# Testing introspection, revocation, and consent

Open this reference when the minimal validation in [SKILL.md](../SKILL.md) is not enough and the blocker is introspection, revocation, or consent verification.

- Verify introspection only for the enabled endpoint and caller contract.
- Verify revocation behavior against the same token lifecycle policy used in production.
- Verify remembered consent and requested scopes against the registered-client settings.
