import { invoiceTotal } from "./index.js";

try {
  /** @type {unknown} */
  const input = JSON.parse(process.argv[2] ?? "");
  console.log(invoiceTotal(input));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
