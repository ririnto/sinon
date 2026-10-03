---
description: >-
  Design agent guidance by audience, authority, owner, host, model, and loading time.
metadata:
  reference:
    GPT-6 Astra model:
      model: gpt-6-astra
      url: https://developers.openai.com/api/docs/models/gpt-6-astra.md
    GPT-6.1 Sol model:
      model: gpt-6.1-sol
      url: https://developers.openai.com/api/docs/models/gpt-6.1-sol.md
    GPT-6 Luna model:
      model: gpt-6-luna
      url: https://developers.openai.com/api/docs/models/gpt-6-luna.md
    GPT-6 instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    Using GPT-6:
      url: https://developers.openai.com/api/docs/guides/latest-model.md
    Claude Fable 5.1 model:
      model: claude-fable-5-1
      url: https://platform.claude.com/docs/en/models/fable-5-1/overview.md
    Claude Opus 5.5 model:
      model: claude-opus-5-5
      url: https://platform.claude.com/docs/en/models/opus-5-5/overview.md
    Claude Sonnet 5.5 model:
      model: claude-sonnet-5-5
      url: https://platform.claude.com/docs/en/models/sonnet-5-5/overview.md
    Prompting Claude Fable 5.1:
      model: claude-fable-5-1
      url: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-fable-5-1.md
    Prompting Claude Opus 5.5:
      model: claude-opus-5-5
      url: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5-5.md
    Prompting Claude Sonnet 5.5:
      model: claude-sonnet-5-5
      url: https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-sonnet-5-5.md
    Codex skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    Codex project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
---

# Instruction Authoring

Use this reference when changing agent guidance, skill activation or content, host hooks, or model prompts.

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

## Shared Principles And Local Limits

State the requested outcome, authorized scope, completion condition, and stop condition that apply to the task.
Name the sources to load when the task reaches their condition.
Choose proof for the acceptance criteria and changed behavior, and reuse passing evidence for unaffected behavior.
Do not add tests that restate prose.
Preserve authority, safety boundaries, explicit user requirements, and required source standards when simplifying guidance.
Keep model selection, effort, review, and delegation policy with the workflow that owns those decisions.
Apply unattended-run instructions where the user cannot respond during execution.
Ask for a concise explanation or action summary instead of requesting hidden reasoning.

## Model-Specific Prompt Notes

Use this table only when a task changes prompts for the named model.
Evaluate each observation on the named model and workload before applying it to another model.
Effort names do not represent equal amounts of reasoning across models.
API documentation describes API behavior and defaults, not the settings or capabilities of an installed host.
Check the target host configuration before changing host defaults or relying on an API feature.
Limit request schema, thinking, display, and history handling to API or harness integration tasks.
Read current official sources before changing model behavior or defaults.

| Model | Source-scoped observation for prompt authors |
| --- | --- |
| GPT-6 Astra | Limit forced reading and repeated checks to task needs, and define completion to prevent early stops. |
| GPT-6.1 Sol | Compare quality and cost on complex target tasks before adopting Astra-specific prompt adjustments. |
| GPT-6 Luna | Evaluate prompts on focused target workloads before adopting Astra-specific adjustments. |
| Claude Fable 5.1 | Start API effort evaluations at `high`, compare other levels, and check retrieval at `low`. |
| Claude Opus 5.5 | Start API effort evaluations at `medium`, and bound unattended continuations by remaining work and concrete blockers. |
| Claude Sonnet 5.5 | Compare `medium` for defined agentic tasks with `high` for harder work, and check verification at `low`. |

For Sonnet at `xhigh` or `max`, keep self-started reviews and additions within the requested scope.
