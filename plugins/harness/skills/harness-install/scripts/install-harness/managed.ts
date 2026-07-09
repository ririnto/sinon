// -*- coding: utf-8 -*-

/** Managed-block begin marker comment. */
export const managedBeginMarker = "<!-- harness:managed begin -->";

/** Managed-block end marker comment. */
export const managedEndMarker = "<!-- harness:managed end -->";

/**
 * Strip trailing whitespace so the managed body layout is controlled by the helpers.
 *
 * @param content Managed body content.
 * @returns Content without trailing whitespace.
 */
const trimTrailing = (content: string): string => content.replace(/\s+$/u, "");

/**
 * Check whether content already contains one managed block.
 *
 * @param content Content to inspect.
 * @returns True when both begin and end markers are present in order.
 */
export const hasManagedBlock = (content: string): boolean => {
  const beginIndex = content.indexOf(managedBeginMarker);
  if (beginIndex === -1) {
    return false;
  }
  return content.includes(managedEndMarker, beginIndex);
};

/**
 * Render managed body as a fresh managed block.
 *
 * @param content Managed body content.
 * @returns Fresh managed block ending with a trailing newline.
 */
export const renderManagedBlock = (content: string): string =>
  `${managedBeginMarker}\n${trimTrailing(content)}\n${managedEndMarker}\n`;

/**
 * Replace the managed block in existing content, preserving any user-authored text outside it.
 *
 * When existing content already has a managed block, only the region between the first
 * begin marker and the last end marker is replaced, collapsing duplicates to one block.
 * When existing content has no managed block, the managed block is prepended so the
 * existing user content is preserved after it.
 *
 * @param existing Existing file content.
 * @param content Managed body content.
 * @returns Updated file content with exactly one managed block.
 */
export const applyManagedBlock = (
  existing: string,
  content: string
): string => {
  const body = trimTrailing(content);
  const beginIndex = existing.indexOf(managedBeginMarker);
  const endIndex = existing.lastIndexOf(managedEndMarker);
  if (beginIndex !== -1 && endIndex !== -1 && beginIndex < endIndex) {
    const before = existing.slice(0, beginIndex);
    const after = existing.slice(endIndex + managedEndMarker.length);
    return `${before}${managedBeginMarker}\n${body}\n${managedEndMarker}${after}`;
  }
  if (existing.trim() === "") {
    return `${managedBeginMarker}\n${body}\n${managedEndMarker}\n`;
  }
  return `${managedBeginMarker}\n${body}\n${managedEndMarker}\n\n${trimTrailing(
    existing
  )}\n`;
};
