# Provider Environment Variables

## Environment Variable Syntax

Environment variable substitution works in provider YAML config values only, not inside dashboard JSON definition files.

Syntax rules:

| Syntax | Behavior |
| --- | --- |
| `$ENV_VAR` | Replaced with value of `ENV_VAR`. Prefer this form when the resolved value itself may contain `$`. |
| `${ENV_VAR}` | Replaced with value of `ENV_VAR`. Use this form only when adjacent literal text would otherwise be parsed as part of the variable name. |
| `$$` | Escapes a literal dollar sign. Produces `$` in the resolved value. |

Two-pass replacement order:

1. First pass: replace `${ENV_VAR}` patterns with environment variable values.
2. Second pass: replace `$ENV_VAR` patterns.

This means `${ENV_VAR}` can be reinterpreted if the substituted value itself contains `$`.
Use `$ENV_VAR` for those cases because Grafana resolves it in the second pass and does not run a third substitution pass over the inserted value.

Example provider using environment variables:

```yaml
apiVersion: 1
providers:
  - name: '$DASHBOARD_PROVIDER_NAME'
    type: file
    options:
      path: '${DASHBOARD_PATH}/dashboards'

```

Resolved at runtime with `DASHBOARD_PROVIDER_NAME=prod-dashboards` and `DASHBOARD_PATH=/opt/grafana`:

```yaml
apiVersion: 1
providers:
  - name: 'prod-dashboards'
    type: file
    options:
      path: '/opt/grafana/dashboards'

```

Constraints:

- Substitution only occurs in provider YAML values, never in dashboard `.json` files.
- Use `$ENV_VAR` instead of `${ENV_VAR}` when the resolved value may itself contain `$`.
- Example: if `DASHBOARD_PROVIDER_NAME=ops$team`, `name: $DASHBOARD_PROVIDER_NAME` resolves to `ops$team`, but `name: ${DASHBOARD_PROVIDER_NAME}` resolves to `ops` when `team` is unset because the second pass treats `$team` as another variable.
- Use `$$` only when the YAML value itself must contain a literal `$` character.

Use when: the blocker is parameterizing provider configs across environments without maintaining duplicate YAML files.
