import { lint } from "markdownlint/sync";
import { expect, test } from "vitest";

import noRelativeSkillLinks from "../tooling/pnpm/rules/no-relative-skill-links.js";

test("relative skill destinations remain visible through generated prose contexts", () => {
  for (const directory of ["guide", "nested/path", "../other"]) {
    for (const suffix of ["", "?view=1", "#usage"]) {
      for (const prefix of ["", "Intro.\n\n", "- Item\n\n"]) {
        const destination = `${directory}/SKILL.md${suffix}`;
        const input = `${prefix}[Read](${destination})\n`;
        const errors =
          lint({
            config: { default: false, "docs/no-relative-skill-links": true },
            customRules: [noRelativeSkillLinks],
            strings: { input }
          }).input ?? [];
        expect(errors).toHaveLength(1);
        expect(errors[0]?.lineNumber).toBe(prefix.split("\n").length);
      }
    }
  }
});

test("generated mixed contexts preserve diagnostics after skipped containers", () => {
  for (const directory of ["guide", "nested/path", "../other"]) {
    for (const suffix of ["", "?view=1", "#usage"]) {
      const link = `[Read](${directory}/SKILL.md${suffix})`;
      const input = `\`\`\`md\n${link}\n\`\`\`\n\n> ${link}\n\n> [!NOTE]\n> ${link}\n\n${link}\n`;
      const errors =
        lint({
          config: { default: false, "docs/no-relative-skill-links": true },
          customRules: [noRelativeSkillLinks],
          strings: { input }
        }).input ?? [];
      expect(errors.map((error) => error.lineNumber)).toEqual([8, 10]);
    }
  }
});

test("generated external skill destinations never produce relative-link diagnostics", () => {
  for (const origin of ["https://example.org", "/absolute", "#section"]) {
    for (const directory of ["guide", "nested/path"]) {
      const input = `[Read](${origin}/${directory}/SKILL.md)\n`;
      expect(
        lint({
          config: { default: false, "docs/no-relative-skill-links": true },
          customRules: [noRelativeSkillLinks],
          strings: { input }
        }).input ?? []
      ).toHaveLength(0);
    }
  }
});
