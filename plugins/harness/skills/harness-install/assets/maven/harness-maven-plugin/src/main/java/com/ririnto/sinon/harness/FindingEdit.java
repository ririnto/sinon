package com.ririnto.sinon.harness;

/**
 * Concrete text edit that a fix would apply. Lines and columns are 1-indexed;
 * end positions are exclusive.
 *
 * @param file        repository-relative POSIX path of the file to edit
 * @param startLine   1-indexed start line
 * @param startColumn 1-indexed start column
 * @param endLine     1-indexed end line
 * @param endColumn   1-indexed end column (exclusive)
 * @param replacement text that replaces the selected range
 */
public record FindingEdit(
        String file,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn,
        String replacement) {}
