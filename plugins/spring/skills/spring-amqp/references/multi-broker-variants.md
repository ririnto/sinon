# Multi-broker variants

Open this reference when the ordinary single-broker path in [SKILL.md](../SKILL.md) is not enough and the blocker is isolating multiple broker connections, templates, or listener factories.

## Multi-broker blocker

**Problem:** different workloads must publish to or consume from different broker connections.

**Solution:** isolate connection factories, templates, and listener factories by broker role.

- Keep each broker path explicit in bean naming and configuration.
- Avoid sharing routing-key or queue-name assumptions across brokers unless the contract is truly identical.
- Keep each listener factory bound to the broker connection it actually serves.

## Pitfalls

- Do not hide multiple brokers behind ambiguous bean names.
- Do not reuse one listener factory across incompatible broker paths.
- Do not assume topology names are portable across brokers without an explicit contract.
