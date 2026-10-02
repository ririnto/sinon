import { main } from "./sdd/cli.js";

process.exitCode = main(process.argv.slice(2));
