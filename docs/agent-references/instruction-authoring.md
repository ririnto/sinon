---
description: >-
  Design agent guidance by audience, authority, owner, host, model, and loading time.
metadata:
  reference:
    Claude prompting best practices:
      url: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices.md
    ChatGPT best practices:
      url: https://learn.chatgpt.com/guides/best-practices
    OpenAI latest-model guidance:
      url: https://developers.openai.com/api/docs/guides/latest-model.md
    GPT-6 instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    Codex skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    Codex project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
---

# Instruction Authoring

Use this reference when changing agent guidance, skill activation or content, host hooks, or model prompts.
Apply these authoring rules to agent-facing instructions and instruction-management skills, not ordinary human-facing prose.
The cited prompting guides do not authorize changes to models, SDKs, configuration, schedules, or installed assets.

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

## Keep Rules With Their Owners

Put repository-wide authoring policy at the root and component-specific authoring rules with their component.
Use official OpenAI guidance for Codex behavior and shared instruction-design patterns where they apply.
Use matching vendor documentation for host-specific, language-specific, runtime, and model-specific behavior.
Label repository preferences as local rules when official sources describe product behavior.
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

Use direct verbs and specify the required output's audience, format, and level of detail.
Give numbered steps when action order matters.
Separate instructions from source material, examples, and variable inputs with clear headings or descriptive delimiters.
Add relevant examples when the required behavior or output would otherwise be ambiguous.
Describe the required result instead of adding blanket tool demands or escalating emphasis.
Keep rules that change a relevant decision or action.
Inspect the instructions agents actually receive for missing, stale, or conflicting rules.

## Shared Principles And Local Limits

State the requested outcome, relevant inputs and context, constraints, and authorized scope.
Name the deliverable, observable completion criteria, and stop conditions before prescribing steps.
Make approval boundaries explicit and continue work already authorized by the user.
Ask focused questions when missing information could materially change the outcome or authority.
Use available evidence for routine gaps within granted scope and report material assumptions.

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

Evaluate prompt behavior on the named model and workload.
Effort names do not represent equal amounts of reasoning across models.
API documentation describes API behavior and defaults, not the settings or capabilities of an installed host.
Check the target host configuration before changing host defaults or relying on an API feature.
Limit request schema, thinking, display, and history handling to API or harness integration tasks.
Read current official sources before changing model behavior or defaults.
