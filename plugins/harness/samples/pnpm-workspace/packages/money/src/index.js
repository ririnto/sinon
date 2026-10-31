export const multiplyCents = (unitCents, quantity) => {
  if (!Number.isSafeInteger(unitCents) || unitCents < 0) {
    throw new RangeError("Unit price must be a non-negative integer in cents.");
  }
  if (!Number.isSafeInteger(quantity) || quantity < 0) {
    throw new RangeError("Quantity must be a non-negative integer.");
  }
  const result = unitCents * quantity;
  if (!Number.isSafeInteger(result)) {
    throw new RangeError("Line total exceeds the safe integer range.");
  }
  return result;
};
