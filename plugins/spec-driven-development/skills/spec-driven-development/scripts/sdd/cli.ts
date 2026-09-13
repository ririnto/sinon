import { optionBool, parseArgs, printHelp } from "./args.js";
import { cmdGenerateDiagram } from "./commands/generate-diagram.js";
import { cmdGetFrontmatter } from "./commands/get-frontmatter.js";
import { cmdListFrontmatter } from "./commands/list-frontmatter.js";
import { cmdListTags } from "./commands/list-tags.js";
import { cmdValidate } from "./commands/validate.js";
import { fail } from "./infrastructure.js";

/**
 * Runs the SDD command line and returns the process exit code.
 */
export const main = (argv: readonly string[]): number => {
  const args = parseArgs(argv);
  if (!args || optionBool(args, "help")) {
    printHelp();
    return args ? 0 : 1;
  }
  switch (args.command) {
    case "get-frontmatter": {
      return cmdGetFrontmatter(args);
    }
    case "list-frontmatter": {
      return cmdListFrontmatter(args);
    }
    case "list-tags": {
      return cmdListTags(args);
    }
    case "generate-diagram": {
      return cmdGenerateDiagram(args);
    }
    case "validate": {
      return cmdValidate(args);
    }
    default: {
      fail(`FAIL: unknown command: ${args.command}`);
      printHelp();
      return 1;
    }
  }
};
