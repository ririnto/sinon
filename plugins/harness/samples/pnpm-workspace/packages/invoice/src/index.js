import { multiplyCents } from "@sample/money";
import { z } from "zod";

const linesSchema = z.array(
  z.object({
    quantity: z.number().int().nonnegative(),
    unitCents: z.number().int().nonnegative()
  })
);

export const invoiceTotal = (input) => {
  let total = 0;
  for (const line of linesSchema.parse(input)) {
    const sum = total + multiplyCents(line.unitCents, line.quantity);
    if (!Number.isSafeInteger(sum)) {
      throw new RangeError("Invoice total exceeds the safe integer range.");
    }
    total = sum;
  }
  return total;
};
