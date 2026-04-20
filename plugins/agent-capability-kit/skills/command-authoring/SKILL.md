---
name: command-authoring
description: Create or refactor Claude Code commands and prompt files with clear arguments, frontmatter, reusable prompt structure, and bounded tool expectations. Use when authoring or reviewing `commands/*.md` files offline.
---

# Command Authoring

Create or refactor one Claude Code command or prompt file under `commands/*.md` so it is easy to invoke, unambiguous at runtime, and self-contained for normal use.

## Operating rules

- Keep the scope on command and prompt files under `commands/*.md`.
- Write commands as instructions to Claude, not as explanatory prose for the user.
- Keep one command focused on one coherent job.
- Put the ordinary authoring path in the command file itself. Do not require external documentation or web links.
- Add frontmatter only for fields the command actually needs.
- Keep tool assumptions narrow and explicit.
- Make arguments, ambiguity behavior, and expected output visible in the command body whenever they affect ordinary use.

## Ordinary authoring procedure

1. Confirm the command job and target file.
   - Use an action-first file name such as `commands/review-doc.md` or `commands/gather-context.md`.
   - Keep the command focused on a single outcome.
2. Draft the smallest frontmatter that supports discovery and safe execution.
   - `description` is the common default and SHOULD explain both capability and trigger condition.
   - Add other fields only when the command needs them during normal use.
3. Write the command body in execution order.
   - State the goal.
   - State required inputs or arguments.
   - State hard constraints and forbidden actions.
   - State the ordered execution steps.
   - State ambiguity behavior.
   - State the exact output contract.
4. Check argument handling.
   - If the command depends on runtime arguments, document them explicitly in frontmatter and body.
   - If the command does not need arguments, omit `argument-hint` and avoid placeholder wording.
5. Check tool and runtime boundaries.
   - Limit `allowed-tools` to the smallest practical set.
   - Add runtime-specific fields only when they materially change how the command is invoked or evaluated.
6. Verify the file reads cleanly as a standalone prompt.
   - A first-time reader should understand what to provide, what Claude must do, how ambiguity is handled, and what the final response must contain without opening a reference.

## Common frontmatter fields

Use only the fields that the command needs.

### Required in most commands

- `description`

Use `description` to explain what the command does and when to use it. Prefer the pattern `Do X. Use when Y.` so the command is discoverable and understandable outside local context.

Example:

```yaml
---
description: Review one Markdown file for structure, clarity, and missing sections. Use when a document needs a fast editorial quality pass.
---
```

### Common optional fields

- `argument-hint`
- `allowed-tools`
- `disable-model-invocation`
- `user-invocable`
- `paths`
- `model`
- `shell`

Use these fields as follows:

- `argument-hint`: add only when the command expects runtime arguments. Show the argument shape, not a prose sentence.
- `allowed-tools`: add when the command should restrict tool access. Keep the list as narrow as possible.
- `disable-model-invocation`: add when the command should not run as a normal model-invoked command path.
- `user-invocable`: set to `false` only for helper commands that should stay out of direct invocation flows.
- `paths`: add when the command should activate or apply only for specific files or directories.
- `model`: add only when the command truly depends on a specific model choice.
- `shell`: add only when shell-specific behavior materially affects the command.

Do not add frontmatter fields as decoration. If removing a field would not change discovery, invocation, or behavior, leave it out.

Example with common optional fields:

```yaml
---
description: Review one pull request for risk, missing tests, and merge blockers. Use when preparing a merge recommendation.
argument-hint: [pr-number]
allowed-tools: Read, Grep, Bash(git *)
disable-model-invocation: true
---
```

## Command body order

For normal authoring, use this order inside the Markdown body after frontmatter:

1. Short title or opening line naming the job
2. Goal or outcome
3. Required inputs or arguments
4. Constraints, guardrails, or forbidden actions
5. Ordered execution steps
6. Ambiguity behavior
7. Output contract

This order keeps commands readable during invocation and makes the runtime path obvious.

Example body shape:

```markdown
# Review Markdown

Review one Markdown file and return a concise rewrite plan.

## Inputs

- `file-path`: target file from `$1`

## Constraints

- Do not edit files.
- Do not invent missing project context.

## Procedure

1. Read the target file.
2. Check heading order, structure, and missing sections.
3. Note concrete fixes with line-aware evidence when possible.

## Ambiguity behavior

- If `$1` is missing, stop and say the command requires a file path.
- If the file is not Markdown, say that the input is out of scope.

## Output contract

Return:

1. The main issues
2. Recommended fixes
3. Any remaining ambiguity
```

## Argument handling rules

- Use `argument-hint` when the caller must supply runtime values.
- Mirror the same argument names in the body so the invocation contract is visible in one read.
- Prefer simple positional shapes such as `[file-path]` or `[source] [destination]`.
- If an argument is optional, mark it clearly and explain the default behavior in the body.
- If the command consumes no arguments, do not mention `$1` or placeholder inputs.
- If missing arguments make the command unsafe or unusable, say so explicitly in the ambiguity section.

Good:

```yaml
---
description: Summarize one log file for error clusters and likely causes. Use when triaging a local failure report.
argument-hint: [log-file]
allowed-tools: Read, Grep
---
```

```markdown
## Inputs

- `log-file`: path from `$1`

## Ambiguity behavior

- If `$1` is missing, stop and request the log file path.
```

Avoid:

```markdown
Review the file the user probably means and do the usual thing.
```

## Ambiguity behavior

Commands should resolve ordinary cases without extra discussion. When ambiguity remains, use the smallest safe behavior.

- If the command can proceed safely with a clear default, state and use that default.
- If a required argument, file, or scope is missing, stop and say exactly what is missing.
- If several interpretations are plausible, choose the simplest valid interpretation and say what you assumed.
- If the command must not take certain actions, state that limit directly in the constraints section.

Example:

```markdown
## Ambiguity behavior

- If the target path contains multiple matching files, review only the file named in `$1`.
- If `$1` points outside `docs/`, continue only if the command explicitly allows broader scope.
- If required context is absent, report the blocker instead of guessing.
```

## Output expectations

Every command should say what the final response must contain.

- If the command produces analysis, list the required sections or numbered items.
- If the command produces an artifact, require the final file path and the full artifact contents when appropriate.
- If the command may leave unresolved risk, require a short remaining-risk note.
- Keep the output contract concrete enough that two authors would produce materially similar responses.

Example:

```markdown
## Output contract

Return:

1. The final command file path
2. The full Markdown command
3. A short note explaining the argument model
4. A short note explaining any remaining blocker or risk
```

## First safe local checks

Use simple local inspection before deeper validation:

```text
Read `commands/your-command.md` and confirm:
- the frontmatter contains only needed fields
- the body order is clear
- arguments, ambiguity behavior, and output contract are explicit
```

If you need a starting point or comparison set, use the optional files in this skill directory.

## Pitfalls

- Do not write the command as a background essay about what Claude should do.
- Do not hide ordinary invocation rules in references.
- Do not make users infer required arguments from examples alone.
- Do not leave ambiguity behavior unstated when missing inputs or scope conflicts are likely.
- Do not omit the expected output shape.

## Output contract

Return:

1. The final command file path
2. The full command Markdown file
3. A short note explaining the argument model
4. A short note explaining the output contract
5. Any explicit remaining blocker or risk

## Optional support files

- `assets/command-template.md` - copy when creating a new command from scratch
- `assets/command-frontmatter-patterns.md` - open when you need quick frontmatter examples for common command types
- `references/command-frontmatter.md` - open when a frontmatter decision is unusual or field interactions need deeper comparison
- `references/prompt-structure.md` - open when the command body needs extended structural examples or repair guidance
