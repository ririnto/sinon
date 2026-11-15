/**
 * Read a nested field while retaining unknown data at fixture boundaries.
 */
export const valueAt = (
  value: unknown,
  ...keys: readonly (string | number)[]
): unknown => {
  const [key, ...remaining] = keys;
  if (key === undefined) {
    return value;
  }
  const record = typeof value === "object" && value !== null ? value : {};
  return valueAt(Reflect.get(record, key), ...remaining);
};

/**
 * Require a fixture object before enumerating its fields.
 */
export const objectValue = (value: unknown): Record<string, unknown> => {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new TypeError("Expected a fixture object.");
  }
  return Object.fromEntries(Object.entries(value));
};

/**
 * Require a fixture string before executing it or comparing string operations.
 */
export const stringValue = (value: unknown): string => {
  if (typeof value !== "string") {
    throw new TypeError("Expected a fixture string.");
  }
  return value;
};

/**
 * Require a fixture array without trusting element types.
 */
export const arrayValue = (value: unknown): readonly unknown[] => {
  if (!Array.isArray(value)) {
    throw new TypeError("Expected a fixture array.");
  }
  return value;
};

/**
 * Treat an absent optional fixture mapping as empty.
 */
export const optionalObject = (value: unknown): Record<string, unknown> =>
  value === undefined ? {} : objectValue(value);
