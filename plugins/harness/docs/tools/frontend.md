---
title: Frontend Baseline
metadata:
  reference:
    design.md:
      url: https://raw.githubusercontent.com/google-labs-code/design.md/main/README.md
    Ultracite:
      url: https://www.ultracite.ai/llms-full.txt
    Storybook:
      version: 10.6.2
      url: https://storybook.js.org/llms-full.txt
    Vercel interface guidance:
      url: https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/AGENTS.md
    Vercel React practices:
      url: https://raw.githubusercontent.com/vercel-labs/agent-skills/main/skills/react-best-practices/AGENTS.md
    Radix Themes:
      url: https://www.radix-ui.com/themes/docs/overview/getting-started
    Radix Colors:
      url: https://www.radix-ui.com/colors/docs/overview/installation
    Radix Icons:
      url: https://www.radix-ui.com/icons
    Radix Primitives:
      url: https://www.radix-ui.com/primitives/docs/overview/getting-started
---

Select this guidance only for real frontend code.
Use the [project baseline](baseline.md) and [pnpm profile](pnpm.md) for its shared configuration.
Keep the upstream reference originals read-only.

## Design And Structure

Create a project-owned `DESIGN.md` using the design.md format and the actual product's design tokens and rationale.
Replace the generated starter before accepting product design work.
Do not copy an example visual identity or invent product requirements.
Use the Vercel interface guidance for applicable semantics, keyboard and focus behavior, responsive layout, reduced motion, and measured rendering performance.
Keep the [shared accessibility rules](../rules.md#frontend-and-accessible-interfaces) as the acceptance floor when an upstream preference differs.
For non-React projects, apply only the relevant interface and JavaScript guidance.
Do not introduce React or Radix React dependencies to satisfy this profile in another framework.

React projects use Radix Themes, Colors, Icons, and Primitives as their primary design building blocks.
The React fragment supplies `@radix-ui/themes`, `@radix-ui/colors`, `@radix-ui/react-icons`, and the `radix-ui` Primitives package through the workspace catalog.
Import the Themes stylesheet once at the application entry and wrap the relevant application surface in its `Theme` provider.
Use Primitives where Themes does not cover the required interaction, retaining accessible names and focus behavior.
Read only the applicable Vercel React practices for data dependencies, bundle boundaries, rendering, and measured performance.
Preserve framework boundaries and avoid speculative memoization or unrelated architecture changes.

## Lint And Component Proof

Use Ultracite as the only direct lint and format entry point for Oxc.
Its canonical `oxfmt.config.ts` excludes `**/*.md` and sets `trailingComma` to `none`.
Markdown remains owned by Markdownlint CLI2.
`check:design` runs `designmd lint DESIGN.md` for the design.md format and token checks.
Passing that check does not establish a complete visual identity or usable interface.
Read the Ultracite and Storybook `llms-full.txt` references before adapting their integration.
The supplied frontend and React fragments select HTML-Vite and React-Vite Storybook respectively.
Preserve an existing compatible renderer instead of replacing it with these defaults.
Add real stories for changed components and their loading, empty, populated, error, keyboard, and focus states where applicable.
`check:storybook` builds stories with `storybook build --test`, which is a build gate rather than browser interaction proof.
Verify the changed flow in the running UI at desktop and mobile sizes using the target's existing driver.
Report unrun browser interaction or visual checks separately from source and build validation.
