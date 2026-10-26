---
metadata:
  reference:
    Herdr agent skill:
      url: https://herdr.dev/docs/agent-skill/
---

# Lane Supervision

Lane Supervision is a Sinon-owned plugin for supervising assigned existing agent lanes through the Herdr CLI.
It delivers continuation directives and verifies results without taking repository-write ownership.

## Prerequisite

Install the upstream Herdr skill separately:

```sh
npx skills add herdrdev/herdr --skill herdr -g
```

Load the installed `herdr` skill before using `lane-supervision`.
The upstream skill owns Herdr CLI guidance and its environment guard.
Sinon does not bundle that skill or publish a local Herdr package.

## Installation

After registering the Sinon marketplace, install this plugin in Claude Code:

```sh
claude plugin install lane-supervision@sinon
```

For Codex, use:

```sh
codex plugin add lane-supervision@sinon
```

## Package Contents

- `.codex-plugin/plugin.json`: Sinon-owned Codex plugin identity.
- `skills/lane-supervision/SKILL.md`: procedure for supervising assigned existing lanes.
- `LICENSE`: Apache-2.0 license retained with the relocated skill.

Claude Code uses the Sinon marketplace entry and the default `skills/` directory.
Codex uses `.codex-plugin/plugin.json` and the default `skills/` directory.
This package retains the relocated skill's Apache-2.0 license.

## Retained Link Limitation

The relocated `lane-supervision` skill retains its original bytes.
Its relative Herdr-guide link targets `../herdr/SKILL.md#understand-layout-panes-and-agents`, which does not resolve within this package.
Load the installed upstream `herdr` skill for CLI syntax, lifecycle states, and pane operations.
