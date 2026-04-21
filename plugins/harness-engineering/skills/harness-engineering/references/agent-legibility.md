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

## Common mistakes

- Sharing a single application instance across agent tasks. This causes interference and non-deterministic test results.
- Exposing only the application runtime without observability. Agents need logs, metrics, and traces to reason about behavior.
- Skipping teardown. Ephemeral resources must be cleaned up after task completion to avoid resource exhaustion.
- Requiring manual browser testing. If the agent cannot drive the UI programmatically, it cannot validate visual or interaction behavior.
- Using production observability infrastructure for agent validation. Agent worktrees must use isolated, ephemeral stacks.
