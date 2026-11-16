# Rule Validation Details

## promtool Output Interpretation

Successful output:

```text
rules/api-latency.rules.yaml SUCCESS: 1 rules found

```

Error output examples:

Syntax error in the PromQL expression:

```text
rules/api-latency.rules.yaml FAILED: parsing YAML file rules/api-latency.rules.yaml: error parsing rules: 1:13: parse error

```

Runtime alert evaluation error (not reported by `promtool check rules`):

```text
vector contains metrics with the same labelset after applying alert labels

```

Invalid duration format:

```text
rules/api-latency.rules.yaml FAILED: error parsing rules: invalid duration "10"

```

Missing required field:

```text
rules/api-latency.rules.yaml FAILED: error parsing rules: missing required field 'expr'

```

YAML scalar rule:

- Use `|-` for multiline PromQL strings
- Use `>-` for one-line expressions when plain scalars would become fragile or need escaping
- Prefer block scalars over ad hoc quoting when the expression contains YAML-sensitive characters and readability matters
- Round only when the alert contract requires it, because rounding can change threshold decisions
- Choose comparison operators and operand order that express the intended firing condition

Rule lifecycle rule:

- Each group evaluates on its interval and processes rules in declared order
- Without `for`, a matching series becomes active immediately on evaluation
- With `for`, a matching series stays pending until the duration is satisfied
- `keep_firing_for` keeps the alert firing briefly after the condition clears so transient gaps do not create noisy resolve/re-fire churn

## Additional Commands

Validate multiple files at once:

```sh
promtool check rules rules/*.yaml

```

Strict mode (fail on warnings):

```sh
promtool check --strict rules alerts/api-latency.rules.yaml

```
