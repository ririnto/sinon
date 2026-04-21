---
title: {{title_yaml}}
description: >-
  {{description_yaml}}
last_updated: "{{last_updated_date}}"
---

## Contract Scope

This document defines contract units for the capability described in `SPEC.md`.
Contract units represent functions, files, interfaces, classes, or modules.
One CONTRACT.md can contain multiple units.

TODO: Replace all scaffold values in this document.

## Contract Units

### Unit: {{unit_name}}

- Kind: `{{unit_kind}}`
- Location: `{{unit_location}}`
- Responsibility: {{unit_responsibility}}
- Covers requirement(s): `{{requirement_reference}}`
- Covers scenario(s): `{{scenario_references}}`

#### Input Rules ({{unit_name}})

| Item | Type | Required | Rules |
| --- | --- | --- | --- |
| {{input_name}} | {{input_type}} | {{input_required}} | {{input_rules}} |

#### Output Rules ({{unit_name}})

| Item | Type | Guarantee |
| --- | --- | --- |
| {{output_name}} | {{output_type}} | {{output_rules}} |

#### Failure Modes ({{unit_name}})

| Condition | Outcome |
| --- | --- |
| {{failure_condition}} | {{failure_outcome}} |

#### Behavioral Constraints ({{unit_name}})

- Invariant: {{invariant_rule}}
- Edge cases: {{edge_case_rule}}
- Side effects: {{side_effects}}
- Idempotency: {{idempotency_rule}}
- Concurrency: {{concurrency_rule}}
- Async semantics: {{async_rule}}

#### Scenario Mapping ({{unit_name}})

| Scenario | Related requirement | Unit behavior | Expected result |
| --- | --- | --- | --- |
| {{scenario_name}} | {{scenario_requirement}} | {{scenario_behavior}} | |
| | | | {{scenario_result}} |

Add additional `### Unit: ...` sections when needed.
Use unique subsection headings for each unit (for example, `#### Input Rules (Unit Name)`).

## Examples by Unit and Scenario

| Example ID | Unit | Scenario | Purpose |
| --- | --- | --- | --- |
| {{example_id}} | {{example_unit}} | {{example_scenario}} | |
| | | | {{example_purpose}} |

### {{example_id}}: {{example_unit}} / {{example_scenario}}

```yaml
input:
  {{example_input_key}}: "{{example_input_value}}"
output:
  {{example_output_key}}: "{{example_output_value}}"
```
