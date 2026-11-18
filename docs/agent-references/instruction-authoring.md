---
description: >-
  Design agent guidance by audience, authority, owner, host, model, and loading time.
metadata:
  reference:
    Claude prompting best practices:
      url: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices.md
    ChatGPT best practices:
      url: https://learn.chatgpt.com/guides/best-practices
    OpenAI model guidance:
      url: https://developers.openai.com/api/docs/guides/latest-model
    Prompting GPT-6.2 in Codex:
      url: https://learn.chatgpt.com/guides/prompting-gpt-6-2-astra
    GPT-6 instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    Agent Skills specification:
      url: https://agentskills.io/specification
    Codex skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    Codex project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
---

# Instruction Authoring

Apply these authoring rules to agent-facing instructions and instruction-management skills, not ordinary human-facing prose.
Change models, SDKs, configuration, schedules, or installed assets only when the task authorizes those changes.

## Design By Audience And Loading Time

Identify the audience, affected resources, authority, host or model, and loading time before moving or merging a rule.
Identify its canonical owner and keep it available before the governed action.

| Surface | Reader | Loading point | Placement |
| --- | --- | --- | --- |
| Skill catalog description | Agents selecting a skill. | At skill discovery. | Keep activation conditions short and task-specific. |
| Activated `SKILL.md` body | Agents using the selected skill. | After skill selection. | Use a small router for entry constraints and conditional references. |
| Conditional support reference | Agents at a named stage or condition. | When its condition applies. | Hold stage-specific detail and link from the owning skill. |
| Host agents and hooks | Agents running assigned roles or hooks. | At host-defined load points or hook events. | Keep host behavior with the host owner. |
| Installed target copies | Agents in the target repository. | At target skill activation or reference access. | Verify installer rewrites in the target layout. |

The repository Markdown check reads the YAML mapping at the start of each published `skills/<name>/SKILL.md`.
It reports missing or malformed frontmatter and duplicate keys before publication.

## Keep Rules With Their Owners

Put repository-wide authoring policy at the root and component-specific authoring rules with their component.
Use matching vendor documentation for language-specific and runtime-specific behavior.
Keep external host configuration and installer path rewrites with their respective owners.

## Decide What To Consolidate

Consolidate rules that serve the same need and audience at the same loading point, with matching scope and authority.
Choose one owner and verify that each consumer can reach it before the governed action.
Preserve a broad rule and a narrow rule when the narrower rule adds scope, an exception, or a later-stage condition.
Preserve repeated wording when a difference in timing, authority, audience, packaging, or host behavior changes what a reader can do.
Compare the obligations before consolidating identical wording or content loaded together.
Do not create cross-plugin content links or rely on source-repository guidance that the consumer does not receive.

## Repository Examples

Keep package-authoring policy separate from installed consumer guidance, including similar rules in `plugins/harness/docs/rules.md`.
Route Harness `implement` and `review` tasks to their shared rules within the package.
Preserve installer copies and the `../../docs/rules.md` to `../docs/rules.md` rewrite for target access.
Preserve spec-driven-development gate notices at skill activation and detailed gate conditions in the stage-specific workflow.
Use the general function-body rule in `repository-conventions.md` and keep TypeScript spacing exceptions in `typescript.md`.
Keep citation-placement policy in the `Official References` section of `repository-conventions.md`.

## Compose Actionable Instructions

Start with the decision or artifact the reader must deliver and how its consumer will judge it.
Keep always-loaded project instructions for conventions the code, tests, and package documentation cannot communicate.
Give an ordered procedure when sequence affects correctness, permissions, or recovery.
Move optional recipes behind the condition that makes them relevant.
Distinguish examples and supplied data from requirements.

Write handoffs for someone who has not seen the working conversation.
Give the outcome, the evidence that supports it, and any decision the recipient still owns.
When editing existing prose, preserve its technical claims, attribution, and the author's intended scope.

## Shared Principles And Local Limits

State the requested outcome, relevant inputs and context, constraints, and authorized scope.
Name the deliverable, observable completion criteria, and stop conditions before prescribing steps.
Make approval boundaries explicit and continue work already authorized by the user.
Proceed on reversible assumptions and keep independent work moving when one decision is blocked.
Ask for a decision when it would change scope, authorize an irreversible action, or settle a material uncertainty the available evidence cannot resolve.

Name required supporting references, their loading conditions, and paths available to the consumer before the governed action.
Read each selected reference before applying its instructions.
Keep required instruction content bundled rather than relying on external attribution links at runtime.

Choose proof for the acceptance criteria and changed behavior, and reuse passing evidence for unaffected behavior.
Provide concrete check commands or observations so the agent can verify completion.
Do not add tests that restate prose.
Preserve authority, safety boundaries, explicit user requirements, and required source standards when simplifying guidance.
Keep model selection, effort, review, and delegation policy with the workflow that owns those decisions.
When Workgraph governs orchestration, read its installed Main Agent Contract before selecting agents.
Read its matching host reference, `references/claude.md` or `references/codex.md`, before selecting models or effort.
Apply unattended-run instructions where the user cannot respond during execution.
Ask for a concise explanation or action summary instead of requesting hidden reasoning.

## Prompt Evaluation

Exercise changed instructions with a representative request through their actual loading path.
For example, a read-only reviewer can report a missing build result without claiming to have run it.
For a routing change, also check a nearby request that belongs to a different skill.
Record which host or model behavior was observed and which conclusions come from source inspection.

In a workflow that controls reasoning effort, use the investigation depth and consequences to choose among the host's supported settings.
For authorized parallel work, give each worker an independent question or exclusive write scope and the evidence its caller needs.
Leave sequencing, final verification, and shared-state integration with the lead.
Release task-owned temporary resources when their evidence is no longer needed.
Effort names do not represent equal amounts of reasoning across models.
API documentation describes API behavior and defaults, not the settings or capabilities of an installed host.
Check the target host configuration before changing host defaults or relying on an API feature.
Limit request schema, thinking, display, and history handling to API or harness integration tasks.
Read current official sources before changing model behavior or defaults.
