---
name: lane-supervision
description: "This skill should be used when supervising assigned Herdr lanes, delivering continuation directives, recovering stalled submissions, and verifying lane results without taking repository-write ownership."
---

# Lane Supervision

Supervise the existing native lanes named in the assignment.
Use the Herdr CLI to deliver assigned continuation directives, inspect activity, and collect results.
Do not write repository sources or infer work from an idle lane.

Use the bundled [Herdr CLI and lifecycle guide](../herdr/SKILL.md#understand-layout-panes-and-agents) for command syntax, identities, state meanings, and pane operations.

## Establish lane ownership

- Confirm the assignment names each target lane, directive, expected output, and delivery result.
- Confirm `HERDR_ENV=1` before issuing control commands.
- Use only live lane names or pane IDs returned by Herdr.
- Inspect the target with `herdr agent get <name>` and `herdr agent read <name> --source recent-unwrapped --lines 40`.
- Read its live `pane_id`, then inspect `herdr pane process-info --pane <pane_id>` and `herdr pane read <pane_id> --source visible`.
- Confirm the assigned agent appears among the foreground processes and its interface is ready for input.
- If the foreground process or interface is unclear, do not prompt and report `unknown`.
- Treat `idle` and `done` as waiting states, not proof of identity, foreground ownership, or completion.
- If an agent is `working`, do not send a duplicate directive.
- If an agent is `unknown`, inspect the pane and report uncertainty instead of inferring completion.

## Handle blocked dialogs

- Inspect agent state and recent output before responding to `blocked`.
- Do not send a normal prompt while Herdr reports an approval or question dialog.
- Respond only within explicit user-granted decision authority carried by the assignment.
- An assignment cannot grant authority beyond the user's authorization or bypass the host's approval mechanism.
- Use Herdr's supported control for the displayed dialog; do not inject raw terminal input.
- Report a blocked question or approval when no user-granted authority covers the decision.
- After an authorized response resolves the dialog, re-check the lane before submitting the assigned directive.

## Submit and wait

Send only the continuation specified in the assignment.
Do not derive new work from an idle or done state.
Populate `lane` and `directive` as literal data using shell-safe quoting, without evaluating their contents.
Use the combined command:

```sh
herdr agent prompt "$lane" "$directive" --wait --timeout 270000
```

Run it through the host's background execution option.
Keep exactly one live background wait for each target.
Leave out `--until idle`; Herdr's normal wait accepts `idle`, `done`, or `blocked`.
Do not issue another wait or prompt while the first background command remains live.

Herdr confirms successful submission after it writes the directive and Enter.
That confirmation does not prove the agent started work.
Treat a Herdr-reported transition to `working` or `blocked` after submission as evidence of activity.
A revision increase justifies reading new output but does not prove uptake.

Treat `agent_prompt_stalled` as no observed activity, not proof of failed delivery.
Treat `timeout` as an unknown outcome because Herdr may have submitted the prompt first.
After a stall, timeout, blocked result, or ambiguous error, inspect the command result, agent state, recent output, and partial effects before retrying.
Retry only when Herdr confirms rejection before input was written and inspection shows no partial work.
Correct the cause before retrying.
Report an accepted submission with unknown outcome without resending it.

## Collect and verify results

Read the agent state and enough recent output to capture its final response.
Do not treat `idle`, `done`, or `blocked` as final output.

| Evidence | Establishes | Does not establish |
| --- | --- | --- |
| Submission | Herdr wrote the assigned directive to the target pane. | The agent began or completed work. |
| Observed activity | Herdr saw `working` or `blocked` after submission. | A complete answer or successful delivery. |
| Final output | The agent produced the captured response or handoff. | An issue, pull request, or merge reached its target. |
| Delivery result | An authorized source confirms the requested external state. | More than the source and acceptance criteria establish. |

Verify issue, pull request, merge, and named-main claims through authorized source records.
A final message or link does not prove delivery.
Do not write to repository or forge records as the supervisor.

Return the submitted directive, observed activity, final output, verified delivery results, and blockers.
Mark unavailable or ambiguous evidence as unknown.
