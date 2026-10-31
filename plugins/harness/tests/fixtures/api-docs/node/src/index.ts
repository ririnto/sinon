/**
 * Find a label for a nonempty key.
 *
 * @param key - The key to look up.
 * @returns The matching label or undefined when absent.
 */
export const findLabel = (key: string): string | undefined =>
  key === "known" ? "Known label" : undefined;
