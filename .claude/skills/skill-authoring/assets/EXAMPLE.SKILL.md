---
name: roll-dice
description: Roll dice with true randomness. Use this skill when the user asks to roll a die, dice, d6, d20, or any polyhedral die, including requests phrased as "roll for X", "give me a random number from 1 to N for a game", or "simulate a dice roll". Do NOT use for generating cryptographic random values or for statistical sampling — use a purpose-built RNG for those.
license: MIT
---

# roll-dice

## Overview

This skill gives the agent the capability to roll dice with true randomness by shelling out to the host's RNG, rather than generating a number from the model's own prior.

## Workflow

To roll a die, run one of the following commands, substituting `<sides>` for the number of faces:

```bash
shuf -i 1-<sides> -n 1
```

```powershell
Get-Random -Minimum 1 -Maximum (<sides> + 1)
```

For multiple dice, chain with a loop or run the command N times.

## Gotchas

- On macOS, `shuf` is not installed by default. Use `jot -r 1 1 <sides>` instead.
- `Get-Random` without `-SetSeed` is cryptographically secure in recent PowerShell versions but not in Windows PowerShell 5.1; do NOT use it for security-sensitive randomness.

## Output contract

Return the rolled value(s) in the form `Rolled <sides>-sided die: <value>` (or a list for multiple dice). Do not editorialize the result.
