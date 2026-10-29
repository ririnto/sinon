---
title: Frontend Baseline
metadata:
  reference:
    design.md:
      url: https://raw.githubusercontent.com/google-labs-code/design.md/main/README.md
    Ultracite:
      url: https://www.ultracite.ai/llms-full.txt
    Storybook:
      version: 10.6.5
      url: https://storybook.js.org/llms-full.txt
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
For an established project, preserve its existing design system and tokens.
Adopt this profile's design baseline only when the task selects or establishes the baseline.
Check semantics, keyboard and focus behavior, responsive layout, reduced motion, and measured rendering performance for the selected interface.
Keep the [shared accessibility rules](../rules.md#frontend-and-accessible-interfaces) as the acceptance floor when an upstream preference differs.
For non-React projects, apply only the relevant interface and JavaScript guidance.
Do not introduce React or Radix React dependencies to satisfy this profile in another framework.

React projects without an established design system use Radix Themes, Colors, Icons, and Primitives as their primary design building blocks.
Preserve an existing design system and its components when the project already has one.
The React fragment supplies `@radix-ui/themes`, `@radix-ui/colors`, `@radix-ui/react-icons`, and the `radix-ui` Primitives package through the workspace catalog.
When using the Radix baseline, import the Themes stylesheet once at the application entry and wrap the relevant application surface in its `Theme` provider.
Use Primitives where Themes does not cover the required interaction, retaining accessible names and focus behavior.
For React, check data dependencies, bundle boundaries, rendering, and measured performance for the changed flow.
Preserve framework boundaries and avoid speculative memoization or unrelated architecture changes.

## Lint And Component Proof

Use Ultracite as the only direct lint and format entry point for Oxc.
Its canonical `oxfmt.config.ts` excludes `**/*.md` and sets `trailingComma` to `none`.
Markdown remains owned by Markdownlint CLI2.
Apply the [React effect policy](pnpm.md#react-effect-policy) when the project uses React directly or through a framework dependency.
`check:design` runs `designmd lint DESIGN.md` for the design.md format and token checks.
Passing that check does not establish a complete visual identity or usable interface.
Read the Ultracite and Storybook `llms-full.txt` references before adapting their integration.
The supplied frontend and React fragments select HTML-Vite and React-Vite Storybook respectively.
Preserve an existing compatible renderer instead of replacing it with these defaults.
Add real stories for changed components and their loading, empty, populated, error, keyboard, and focus states where applicable.
`check:storybook` builds stories with `storybook build --test`, which is a build gate rather than browser interaction proof.
Verify the changed flow in the running UI at desktop and mobile sizes using the target's existing driver.
Report unrun browser interaction or visual checks separately from source and build validation.
