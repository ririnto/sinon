# {{workflow_name}}

## Purpose

{{workflow_purpose}}

## Dispatch Criteria

Define which issues or tasks are eligible for autonomous implementation and which require interactive engineering judgment.

## Stage

{{workflow_stage}}

## Owner

{{owner}}

## Workspace Policy

Each task SHOULD run in an isolated workspace.
Workspace names SHOULD be derived from stable identifiers using only safe filename characters.

## Prompt Contract

The agent receives:

- task identifier and summary
- linked product spec or design doc
- active execution plan if one exists
- validation command
- expected proof of work

## Inputs

{{workflow_inputs}}

## Outputs

{{workflow_outputs}}

## Max Concurrency

{{max_concurrency}}

## Handoff State

{{handoff_state}}

## Proof of Work

The agent MUST provide at least one durable proof item:

- passing tests or CI status
- review feedback summary
- complexity or risk analysis
- screenshot, log excerpt, metric, or walkthrough video reference

## Required Evidence

{{required_evidence}}

## Validation

Run `{{validation_command}}` before handoff and include WARN and ERROR output in the run summary.

## Rollback Criteria

{{rollback_criteria}}
