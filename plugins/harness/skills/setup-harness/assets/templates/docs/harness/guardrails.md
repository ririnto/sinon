# Harness Guardrails

Document the repository-specific rules that harness checks and human reviewers enforce for {project-name}.

## Scope

- Repository: `{repository-name}`
- Primary runtime: {runtime or deployment target}
- Harness owner: {team or person responsible for guardrail updates}
- Review cadence: {weekly, per release, or per architecture change}

## Required Practices

| Area | Rule | Evidence |
| --- | --- | --- |
| Architecture | {boundary or dependency rule that must hold} | `{command or doc path}` |
| Testing | {minimum validation required before merge} | `{test command}` |
| Security | {secret handling, dependency, or auth rule} | `{audit command or policy path}` |
| Documentation | {doc that must change with behavior} | `{doc path}` |

## Blocked Patterns

- {disallowed pattern and why it is unsafe for this repository}
- {disallowed shortcut that previously caused drift or incidents}

## Exceptions

| Exception | Owner | Expiry | Tracking |
| --- | --- | --- | --- |
| {temporary exception} | {owner} | {date or release} | `{issue or plan path}` |

## Validation

```bash
{primary harness validation command}
{architecture or dependency guard command}
```
