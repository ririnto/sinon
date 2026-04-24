# Agent Legibility

Open this reference when configuring per-worktree isolation, browser automation, or observability stacks beyond the common-path guidance in `SKILL.md`.

## Why agent legibility matters

From the agent's point of view, anything it cannot access in-context while running effectively does not exist. The application UI, logs, metrics, and traces must be directly observable by the agent for it to reproduce bugs, validate fixes, and reason about behavior.

## Per-worktree isolation

Make the application bootable per git worktree so each agent task gets a fully isolated instance.

### Setup pattern

```text
1. Create a git worktree for the task:
   git worktree add ../task-123 feature-branch

2. Boot the application in the worktree:
   cd ../task-123 && ./scripts/boot-isolated.sh

3. The boot script:
   - Generates a unique port assignment
   - Creates an ephemeral data directory
   - Starts the application with isolated config
   - Waits for health check to pass
   - Prints the base URL for agent access

4. Agent performs its work against the isolated instance.

5. On task completion, tear down:
   ./scripts/teardown-isolated.sh ../task-123
```

### Boot script sketch

```sh
# :description: Boot an isolated application instance in a worktree.
# :param worktree_path: Path to the git worktree directory.
boot_isolated() {
    worktree_path="$1"
    port=$(assign_free_port)
    data_dir="${worktree_path}/.runtime/data"
    mkdir -p "${data_dir}"

    APP_PORT="${port}" \
    APP_DATA_DIR="${data_dir}" \
    APP_LOG_DIR="${worktree_path}/.runtime/logs" \
    "${worktree_path}/scripts/start-app" &

    wait_for_health "http://localhost:${port}/health"
    echo "http://localhost:${port}"
}
```

### Key invariants

- Each worktree gets its own port, data directory, and log directory.
- No shared state between worktrees.
- The ephemeral stack is torn down when the task completes.
- The agent can start, stop, and restart the instance independently.

## Chrome DevTools Protocol integration

Connect browser automation so the agent can validate UI behavior directly.

### Capabilities

- DOM snapshots: capture the current DOM state for structural assertions.
- Screenshots: capture visual state for regression comparison.
- Navigation: drive the browser through user flows.
- Console capture: collect browser console output for error detection.

### Integration pattern

```python
"""
Agent skill: capture a DOM snapshot of the current page.

:param page_url: The URL to navigate to before capturing.
:param selector: Optional CSS selector to scope the snapshot.
:return: The DOM snapshot as a structured string.
"""
async def capture_dom_snapshot(page_url, selector=None):
    async with async_playwright() as p:
        browser = await p.chromium.launch()
        page = await browser.new_page()
        await page.goto(page_url)
        if selector:
            element = await page.query_selector(selector)
            return await element.inner_html()
        return await page.content()
```

### Video recording for bug reproduction

```python
"""
Agent skill: record a video demonstrating a bug or its fix.

:param page_url: The URL to navigate to.
:param actions: List of (action, target) tuples to perform.
:param output_path: Where to save the video file.
:return: Path to the recorded video.
"""
async def record_video(page_url, actions, output_path):
    async with async_playwright() as p:
        browser = await p.chromium.launch(
            record_video_dir=str(Path(output_path).parent)
        )
        context = await browser.new_context()
        page = await context.new_page()
        await page.goto(page_url)

        for action, target in actions:
            if action == "click":
                await page.click(target)
            elif action == "type":
                await page.type(target[0], target[1])
            elif action == "wait":
                await page.wait_for_selector(target)

        await context.close()
        return output_path
```

## Local observability stack

Expose logs, metrics, and traces through an ephemeral local stack per worktree.

### Stack components

| Component | Purpose | Agent interface |
| --- | --- | --- |
| Logs | Structured application logs | LogQL queries |
| Metrics | Performance and business metrics | PromQL queries |
| Traces | Distributed request traces | Trace ID lookup |

### Configuration pattern

```yaml
# Ephemeral observability config per worktree.
# Each worktree gets its own instance on isolated ports.

logs:
  backend: loki
  endpoint: "http://localhost:${LOKI_PORT}"
  query_language: logql

metrics:
  backend: prometheus
  endpoint: "http://localhost:${PROMETHEUS_PORT}"
  query_language: promql

traces:
  backend: tempo
  endpoint: "http://localhost:${TEMPO_PORT}"
```

### Agent query examples

Agents can formulate prompts that translate directly into observability queries:

```text
Prompt: "Ensure service startup completes in under 800 ms"
Query:  histogram_quantile(0.99, rate(startup_duration_seconds_bucket[5m]))

Prompt: "No span in these four critical user journeys exceeds two seconds"
Query:  max(duration_seconds) by (journey) > 2
        or max_over_time(duration_seconds[1h]) by (journey) > 2
```

## End-to-end agent validation flow

With all legibility components in place, a single agent run can:

1. Boot an isolated application instance in a worktree.
2. Reproduce a reported bug by driving the UI.
3. Record a video demonstrating the failure.
4. Implement a fix.
5. Validate the fix by driving the application again.
6. Record a second video demonstrating the resolution.
7. Query observability data to confirm performance constraints.
8. Open a pull request with evidence attached.

## Long-running single runs

Single agent runs regularly span several hours. A run MUST be able to survive runtime restarts, context truncation, and intentional checkpoint-and-resume without losing state.

### Checkpoint layout per worktree

Store checkpoints inside the worktree so they share the same isolation boundary as logs and data.

```text
{worktree_path}/.runtime/checkpoints/
├── plan.md
├── progress.jsonl
├── evidence/
│   ├── before.mp4
│   ├── after.mp4
│   ├── before-dom.html
│   └── after-dom.html
└── last_step.txt
```

Key invariants:

- `plan.md` is the execution plan the run is following. It MUST be committed to the repository before the run starts so a resuming agent can load it from git rather than from volatile state.
- `progress.jsonl` is append-only. Each line records one completed step with a timestamp and a short outcome note.
- `evidence/` holds artifacts that would otherwise be recomputed on resume, such as reproduction videos, DOM snapshots, and observability query results.
- `last_step.txt` stores the identifier of the last completed step so a resuming agent can skip finished work.

### Progress record format

```json
{"step": "reproduce-bug", "status": "completed", "ts": "2026-04-24T12:03:11Z", "evidence": ["evidence/before.mp4", "evidence/before-dom.html"]}
{"step": "implement-fix", "status": "completed", "ts": "2026-04-24T13:47:02Z", "evidence": ["src/service/retry.py"]}
{"step": "validate-fix", "status": "in_progress", "ts": "2026-04-24T14:05:44Z"}
```

Each line is one JSON object and is appended atomically. A resuming agent reads the file sequentially and resumes from the last `in_progress` or from the next unlisted step in `plan.md`.

### Resume protocol

1. Load the worktree and read `.runtime/checkpoints/plan.md`.
2. Replay `progress.jsonl` to rebuild the set of completed steps and the last in-flight step.
3. Verify checkpoint evidence still exists and matches the recorded paths. Treat any missing file as a step that must be re-run.
4. Rehydrate the observability stack and the application instance using the worktree's existing ports and data directory. Do not allocate fresh ports; they belong to the original run.
5. Continue execution from the first unfinished step. Append new progress entries rather than rewriting earlier ones.
6. Tear the worktree down only after the full plan is marked complete in `progress.jsonl` and every declared evidence artifact is present.

### Checkpoint cadence

- After every structural boundary: reproduction, fix, validation, observability check, pull-request open.
- Before any action that consumes significant wall time, such as a long test run or a multi-step browser journey.
- Never in the middle of a write that could leave the working tree partially updated; complete the write, then checkpoint.

### Common long-run failures

- Losing the worktree because the host pruned stale branches. Protect worktrees used for long runs with a naming convention that the cleanup job ignores.
- Running out of disk because evidence artifacts accumulated. Apply a retention policy per worktree, for example keeping only the most recent before-and-after pair.
- Resuming against a fresh application instance that has lost ephemeral state. Always resume inside the same worktree and observability stack that produced the earlier checkpoints.
- Rewriting `progress.jsonl` instead of appending. A rewritten history hides prior decisions and makes failure analysis impossible.

## Common mistakes

- Sharing a single application instance across agent tasks. This causes interference and non-deterministic test results.
- Exposing only the application runtime without observability. Agents need logs, metrics, and traces to reason about behavior.
- Skipping teardown. Ephemeral resources must be cleaned up after task completion to avoid resource exhaustion.
- Requiring manual browser testing. If the agent cannot drive the UI programmatically, it cannot validate visual or interaction behavior.
- Using production observability infrastructure for agent validation. Agent worktrees must use isolated, ephemeral stacks.
