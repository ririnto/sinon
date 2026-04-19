# Spring Web Flow scopes

Open this reference when the ordinary wizard flow in `SKILL.md` is not enough and the task needs scope tradeoffs, scope searching, or longer-lived conversation state.

## Scope decisions

- `requestScope`: data needed only for the current request.
- `flashScope`: data that should survive one redirect or transition.
- `viewScope`: state tied to the current rendered view.
- `flowScope`: state that must survive the whole flow execution.
- `conversationScope`: state shared across nested flows or a longer conversation.

Prefer the narrowest scope that preserves the required state.

## Scope boundary

- Use `flowScope` for the ordinary multi-step form object.
- Use `viewScope` when the data belongs only to the current rendered page.
- Use `conversationScope` only when nested flows or a longer conversation genuinely share state.

## Gotchas

- Do not put large mutable graphs into `conversationScope` casually.
- Do not use `conversationScope` when `flowScope` is sufficient.
- Do not assume every scope survives the same redirects, backtracking, or nested-flow boundaries.
