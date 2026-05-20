---
name: multi-agent-coordination
description: |-
  Practical multi-agent workflows using plugin settings files, coordinator sessions, task tracking, and real-world swarm patterns.
---

# Multi-Agent Coordination: Task Numbering, Sessions, and Swarm Patterns

This reference covers practical multi-agent workflows using plugin settings files, coordinator sessions, task tracking, and real-world patterns adapted from production plugins.

## Task numbering convention

Agent tasks use dotted notation: `<major>.<minor>[.<sub>]`

- `1` — Top-level task (e.g., "Build authentication system")
- `1.1` — Subtask (e.g., "Implement JWT token generation")
- `1.1.1` — Sub-subtask (e.g., "Add token refresh endpoint")
- `1.5` — Sibling to `1.1` in same parent task

Ordering rules:

- Numbers are compared numerically: `1.10` sorts after `1.2`
- Tasks are referenced by full path: `3.2.1` means task 1 of phase 2 of major task 3
- Task numbering is assigned by coordinator at start of swarm

## Coordinator session setup

Multi-agent swarms need a coordinator process that tracks progress and distributes work.

### Option 1: tmux-based coordinator

Coordinator runs in tmux session, receives messages from agents:

```bash
#!/bin/bash
set -euo pipefail

# Start coordinator session for multi-agent swarm.
# @param swarm_name Name of swarm (used as tmux session name).
# @return Creates tmux session and starts listening loop.
start_coordinator() {
    local swarm_name="$1"
    tmux new-session -d -s "$swarm_name" -c "$(pwd)"
    tmux send-keys -t "$swarm_name" "while read msg; do echo \"[$(date)] \$msg\"; done" Enter
    echo "Coordinator '$swarm_name' ready"
}
```

Agents send messages to coordinator:

```bash
#!/bin/bash

# Validate coordinator session name for safety.
# @param session_name Tmux session identifier.
# @return Exits 0 if valid (alphanumeric, hyphen, underscore); exits 1 otherwise.
validate_session_name() {
    local session_name="$1"
    if [[ ! "$session_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        echo "Error: invalid session name '$session_name'" >&2
        return 1
    fi
    return 0
}

# Report task completion to coordinator.
# @param coordinator_session Tmux session name (validated).
# @param task_number Task identifier.
# @param status Completion status.
# @return Sends message to coordinator via tmux.
report_to_coordinator() {
    local coordinator_session="$1"
    local task_number="$2"
    local status="$3"
    if ! validate_session_name "$coordinator_session"; then
        return 1
    fi
    local safe_status
    safe_status=$(printf '%q' "$status")
    tmux send-keys -t "$coordinator_session" "Agent $HOSTNAME completed task $task_number: $safe_status" Enter
}
```

### Option 2: File-based coordinator

Coordinator watches a shared directory for status files:

```bash
#!/bin/bash

# Write agent status to coordinator file.
# @param task_dir Shared directory for status files.
# @param agent_name Name of this agent.
# @param task_number Task identifier.
# @param status Status message.
# @return Creates status file in shared directory.
write_status() {
    local task_dir="$1"
    local agent_name="$2"
    local task_number="$3"
    local status="$4"
    mkdir -p "$task_dir"
    local status_file="${task_dir}/${agent_name}.${task_number}.status"
    {
        echo "agent=$agent_name"
        echo "task=$task_number"
        echo "status=$status"
        echo "timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$status_file"
}
```

Coordinator polls directory:

```bash
watch -n 2 "ls -la /tmp/swarm-status/"
```

## Plugin settings for agent identity

Each agent in swarm stores identity and task in `.claude/<plugin-name>.local.md`:

### Agent settings template

```markdown
---
agent_name: auth-service-agent
task_number: 3.2
pr_number: 1234
coordinator_session: project-coordinator
enabled: true
dependencies:
  - "3.1"
---

# Task 3.2: Implement JWT Authentication

Coordinate with database-agent on schema changes (task 3.1).
Blocks downstream API gateway refactoring (task 3.3).

## Current Status

- [ ] JWT token generation (standard claims)
- [ ] Token refresh endpoint
- [ ] Token validation middleware
- [ ] Integration tests
```

Fields:

- `agent_name` — Unique identifier for this agent (e.g., `auth-service-agent`)
- `task_number` — Task in swarm (e.g., `3.2`)
- `pr_number` — Related PR if applicable
- `coordinator_session` — Tmux session name for reporting
- `enabled` — Master on/off flag
- `dependencies` — Prerequisite tasks that must complete first

### Hook reads settings and reports progress

```bash
#!/bin/bash
set -euo pipefail

# Report task progress to coordinator when tool completes.
# @param local_md Path to plugin state file.
# @return Sends progress update to coordinator session.
report_tool_completion() {
    local local_md="$1"
    if [[ ! -f "$local_md" ]]; then
        return 0
    fi
    local fm
    fm=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$local_md")
    local agent_name
    agent_name=$(echo "$fm" | grep '^agent_name:' | sed 's/agent_name: *//')
    local task_number
    task_number=$(echo "$fm" | grep '^task_number:' | sed 's/task_number: *//')
    local coordinator
    coordinator=$(echo "$fm" | grep '^coordinator_session:' | sed 's/coordinator_session: *//')
    if [[ -z "$agent_name" ]] || [[ -z "$coordinator" ]]; then
        return 0
    fi
    if ! validate_session_name "$coordinator"; then
        return 1
    fi
    if [[ ! "$agent_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        echo "Error: invalid agent name '$agent_name'" >&2
        return 1
    fi
    local safe_agent
    safe_agent=$(printf '%q' "$agent_name")
    local safe_task
    safe_task=$(printf '%q' "$task_number")
    tmux send-keys -t "$coordinator" "UPDATE: agent=$safe_agent task=$safe_task status=working" Enter
}
```

## Agent dependency tracking

Agents can declare prerequisites in settings:

```markdown
---
task_number: 3.3
dependencies:
  - "3.1"
  - "3.2"
---
```

Hook checks if dependencies are complete:

```bash
#!/bin/bash

# Block work until prerequisite tasks complete.
# @param task_dir Directory containing status files.
# @param dependencies Space-separated list of required tasks.
# @return Exits 2 if any dependency incomplete; exits 0 otherwise.
check_dependencies() {
    local task_dir="$1"
    shift
    local dependencies=("$@")
    for dep_task in "${dependencies[@]}"; do
        local dep_status
        dep_status=$(ls "$task_dir"/*.${dep_task}.status 2>/dev/null || echo "")
        if [[ -z "$dep_status" ]]; then
            echo "Error: prerequisite task $dep_task not started" >&2
            return 2
        fi
        local status
        status=$(grep '^status=' "$dep_status" | sed 's/^status=//')
        if [[ "$status" != "complete" ]]; then
            echo "Error: prerequisite task $dep_task not complete (status=$status)" >&2
            return 2
        fi
    done
    return 0
}
```

## Real-world example: multi-agent swarm

Three agents working on interconnected tasks:

### Agent 1: Database schema agent (task 1)

**.claude/db-agent.local.md:**

```markdown
---
agent_name: db-schema-agent
task_number: 1
coordinator_session: main-coordinator
dependencies:
  - "1"
---

# Task 1: Design Database Schema

Create schema for user, token, and session tables.
```

### Agent 2: Auth service agent (task 2, depends on task 1)

**.claude/auth-agent.local.md:**

```markdown
---
agent_name: auth-service-agent
task_number: 2
coordinator_session: main-coordinator
dependencies:
  - "1"
---

# Task 2: Implement JWT Auth

Requires task 1 schema to be complete.
```

### Agent 3: API gateway agent (task 3, depends on task 2)

**.claude/gateway-agent.local.md:**

```markdown
---
agent_name: api-gateway-agent
task_number: 3
coordinator_session: main-coordinator
dependencies:
  - "2"
---

# Task 3: Update API Gateway

Integrate auth service from task 2.
```

### Hook: conditional execution on dependencies

```bash
#!/bin/bash
set -euo pipefail

# Check dependencies and allow task to proceed.
# @param local_md Path to agent state file.
# @param status_dir Directory with status files from other agents.
# @return Exits 0 if ready; exits 2 if blocked by dependency.
conditional_start() {
    local local_md="$1"
    local status_dir="$2"
    if [[ ! -f "$local_md" ]]; then
        exit 0
    fi
    local fm
    fm=$(sed -n '/^---$/,/^---$/{ /^---$/d; p; }' "$local_md")
    local deps_line
    deps_line=$(echo "$fm" | grep '^dependencies:' | sed 's/dependencies: *//')
    if [[ -z "$deps_line" ]]; then
        exit 0
    fi
    local deps
    deps=$(echo "$deps_line" | sed 's/\[//;s/\]//' | tr ',' '\n' | sed 's/^ *"//' | sed 's/"$ *//')
    for dep in $deps; do
        local status_file="${status_dir}/*.${dep}.status"
        if ! ls $status_file 2>/dev/null | head -1 >/dev/null; then
            echo '{"continue": false, "systemMessage": "Waiting for task '\"$dep\"' to complete"}' >&2
            exit 2
        fi
        local status
        status=$(grep '^status=' $(ls $status_file | head -1) | sed 's/^status=//')
        if [[ "$status" != "complete" ]]; then
            echo '{"continue": false, "systemMessage": "Task '\"$dep\"' not complete (status='\"$status\"')"}' >&2
            exit 2
        fi
    done
    exit 0
}
```

## Cross-agent communication patterns

### Pattern 1: Shared state directory

All agents write state to `.claude/swarm-state/`:

```bash
mkdir -p .claude/swarm-state

# Agent 1 writes schema info
echo "task_1_schema_version=1" > .claude/swarm-state/task-1.state

# Agent 2 reads schema info
source .claude/swarm-state/task-1.state
```

### Pattern 2: Completion notifications

Agent writes "complete" file when done:

```bash
# Agent 1 at end of task
echo "task_1_completed_at=$(date +%s)" > .claude/swarm-state/task-1.complete
```

Agent 2 waits for file:

```bash
while [[ ! -f .claude/swarm-state/task-1.complete ]]; do
    sleep 2
done
```

### Pattern 3: Configuration propagation

Agent 1 writes config; Agent 2 reads:

```markdown
---
output_format: json
generated_files:
  - src/types.ts
  - src/schema.ts
---
```

Agent 2 reads:

```bash
OUTPUT_FORMAT=$(grep '^output_format:' ".claude/db-agent.local.md" | sed 's/output_format: *//')
```

## Complex example: multi-session coordination

Teams with multiple Claude Code sessions per agent:

```markdown
---
agent_name: backend-team
team_size: 3
sessions:
  - session_id: session-a
    role: auth-implementation
    task_number: 2.1
  - session_id: session-b
    role: database-schema
    task_number: 2.2
  - session_id: session-c
    role: integration-tests
    task_number: 2.3
coordinator_session: backend-leader
---

# Task 2: Backend Implementation

Three parallel sessions coordinated by backend-leader.
```

Coordinator polls all sessions:

```bash
#!/bin/bash

# Aggregate status from multiple agent sessions.
# @return Outputs combined status across all sessions.
aggregate_status() {
    local agent_md=".claude/backend-team.local.md"
    sessions=$(grep -A 20 '^sessions:' "$agent_md" | grep 'session_id:' | awk '{print $2}')
    for session in $sessions; do
        local status
        status=$(tmux capture-pane -t "$session" -p | tail -1)
        echo "$session: $status"
    done
}
```

## Testing multi-agent flow

Simulate multi-agent swarm locally:

```bash
# Start coordinator
tmux new-session -d -s coordinator "while true; do sleep 1; done"

# Create three agent state files
cat > .claude/agent-1.local.md <<EOF
---
agent_name: agent-1
task_number: 1
coordinator_session: coordinator
dependencies: []
---
EOF

cat > .claude/agent-2.local.md <<EOF
---
agent_name: agent-2
task_number: 2
coordinator_session: coordinator
dependencies:
  - "1"
---
EOF

# Hook runs with dependency check
bash hooks/swarm-check.sh .claude/agent-2.local.md .claude/swarm-state
```

## References

Refer to `SKILL.md` for basic settings file patterns and file structure.

Refer to `references/frontmatter-parsing.md` for complete YAML parsing patterns to extract task numbers and dependencies.
