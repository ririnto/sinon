#!/usr/bin/env bun

import fs from "node:fs";
import path from "node:path";
import process from "node:process";

import ts from "typescript";

/**
 * Supported source-file extensions for JSDoc enforcement.
 *
 * @type {Set<string>}
 */
const supportedExtensions = new Set([
  ".js",
  ".jsx",
  ".mjs",
  ".cjs",
  ".ts",
  ".tsx",
]);

/**
 * Tracked source paths read from standard input as a NUL-delimited list.
 *
 * @type {string[]}
 */
const sourceFiles = fs
  .readFileSync(0, "utf-8")
  .split("\0")
  .filter((file) => file.length > 0)
  .filter((file) => supportedExtensions.has(path.extname(file).toLowerCase()));

/**
 * Collected validation findings for files under validation.
 *
 * @type {{ file: string; line: number; column: number; message: string }[]}
 */
const findings = [];

/**
 * Records a finding at the location of the given node.
 *
 * @param {string} file Path to the validated file.
 * @param {ts.Node} node AST node used for line/column reporting.
 * @param {string} message Diagnostic message to emit.
 * @returns {void}
 */
const addFinding = function addFinding(file, node, message) {
  const { line, character } = node
    .getSourceFile()
    .getLineAndCharacterOfPosition(node.getStart());
  findings.push({
    column: character + 1,
    file,
    line: line + 1,
    message,
  });
};

/**
 * Resolves the TypeScript script kind for a file extension.
 *
 * @param {string} filePath File path from stdin.
 * @returns {ts.ScriptKind} Script kind to pass into TypeScript parser creation.
 */
const getScriptKind = function getScriptKind(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  if (ext === ".tsx" || ext === ".jsx") {
    return ts.ScriptKind.TSX;
  }
  if (ext === ".ts") {
    return ts.ScriptKind.TS;
  }
  return ts.ScriptKind.JS;
};

/**
 * Tests whether a node has at least one JSDoc comment attached.
 *
 * @param {ts.Node} node Node being validated.
 * @returns {boolean} True when the node has a JSDoc comment.
 */
const hasJSDoc = function hasJSDoc(node) {
  return ts.getJSDocCommentsAndTags(node).length > 0;
};

/**
 * Returns true for top-level variable/constant declarations.
 *
 * @param {ts.Node} node Candidate AST node.
 * @returns {boolean} True when node is a source-file variable statement.
 */
const isVariableLikeDeclaration = function isVariableLikeDeclaration(node) {
  return (
    ts.isVariableStatement(node) &&
    node.parent.kind === ts.SyntaxKind.SourceFile
  );
};

/**
 * Returns true for function-like nodes that should carry JSDoc under this rule.
 *
 * @param {ts.Node} node Candidate AST node.
 * @returns {boolean} True when node is function-like and not a function-expression child of a variable declaration.
 */
const isFunctionLikeForJSDoc = function isFunctionLikeForJSDoc(node) {
  if (
    !ts.isFunctionDeclaration(node) &&
    !ts.isMethodDeclaration(node) &&
    !ts.isSetAccessorDeclaration(node) &&
    !ts.isGetAccessorDeclaration(node) &&
    !ts.isFunctionExpression(node)
  ) {
    return false;
  }

  if (ts.isFunctionDeclaration(node) && node.body === undefined) {
    return false;
  }

  if (
    ts.isFunctionExpression(node) &&
    node.parent !== undefined &&
    ts.isVariableDeclaration(node.parent)
  ) {
    return false;
  }

  return true;
};

/**
 * Returns true for function declarations that live directly under the source file.
 *
 * @param {ts.Node} node Candidate AST node.
 * @returns {boolean} True when node is a source-file function declaration.
 */
const isTopLevelFunction = function isTopLevelFunction(node) {
  return (
    node.parent !== undefined && node.parent.kind === ts.SyntaxKind.SourceFile
  );
};

/**
 * Returns true for class methods and property accessors.
 *
 * @param {ts.Node} node Candidate AST node.
 * @returns {boolean} True when node is a method/getter/setter declaration.
 */
const isClassMethod = function isClassMethod(node) {
  return (
    ts.isMethodDeclaration(node) ||
    ts.isSetAccessorDeclaration(node) ||
    ts.isGetAccessorDeclaration(node)
  );
};

/**
 * Returns a human-readable name for function/variable diagnostics.
 *
 * @param {ts.Node} node Candidate AST node.
 * @returns {string} Renderable node name.
 */
const nodeName = function nodeName(node) {
  if (ts.isFunctionDeclaration(node) && node.name) {
    return node.name.getText();
  }
  if (ts.isVariableStatement(node)) {
    const [declaration] = node.declarationList.declarations;
    return declaration && declaration.name
      ? declaration.name.getText()
      : "variable declaration";
  }
  if (
    (ts.isMethodDeclaration(node) ||
      ts.isSetAccessorDeclaration(node) ||
      ts.isGetAccessorDeclaration(node)) &&
    node.name
  ) {
    return node.name.getText();
  }
  return "[anonymous]";
};

/**
 * Adds a missing-JSDoc finding for a function- or variable-like node.
 *
 * @param {string} file Path to the validated file.
 * @param {ts.Node} node Node that is missing required JSDoc.
 * @param {string} ruleId Rule id for diagnostics.
 * @returns {void}
 */
const addFunctionMissingTagFinding = function addFunctionMissingTagFinding(
  file,
  node,
  ruleId
) {
  const kind = ts.isVariableStatement(node) ? "variable" : "function";
  addFinding(
    file,
    node,
    `${ruleId}: missing JSDoc for ${kind} "${nodeName(node)}"`
  );
};

/**
 * Extracts raw JSDoc tag type text from source.
 *
 * @param {string} sourceText Full source text of the containing file.
 * @param {ts.JSDocType | undefined} typeNode JSDoc type node.
 * @returns {string} JSDoc tag type text with surrounding trivia removed.
 */
const jsdocTagTypeText = function jsdocTagTypeText(sourceText, typeNode) {
  const start = typeNode.getStart();
  const end = typeNode.getEnd();
  return sourceText.slice(start, end).trim();
};

/**
 * Detects broad JSDoc object types.
 *
 * @param {ts.JSDocType | undefined} typeNode JSDoc type node.
 * @param {string} sourceText Full source text of the containing file.
 * @returns {boolean} True when the type is `object`/`Object`.
 */
const isObjectTagType = function isObjectTagType(typeNode, sourceText) {
  if (!typeNode) {
    return false;
  }
  const typeText = jsdocTagTypeText(sourceText, typeNode);
  if (
    typeNode.kind === ts.SyntaxKind.ObjectKeyword ||
    typeText.toLowerCase() === "object"
  ) {
    return true;
  }
  return (
    ts.isTypeReferenceNode(typeNode) &&
    ts.isIdentifier(typeNode.typeName) &&
    typeNode.typeName.text.toLowerCase() === "object"
  );
};

/**
 * Enforces concrete JSDoc types by rejecting broad object type tags.
 *
 * @param {string} filePath Path to the file under validation.
 * @param {string} sourceText Source text for the file.
 * @param {ts.Node} node Node under validation.
 * @param {string} kind Human-readable node kind label.
 * @returns {void}
 */
const ensureJSDocTypeSafety = function ensureJSDocTypeSafety(
  filePath,
  sourceText,
  node,
  kind
) {
  for (const tag of ts.getJSDocTags(node)) {
    if (tag && tag.typeExpression && tag.typeExpression.type) {
      const tagType = tag.typeExpression.type;
      if (isObjectTagType(tagType, sourceText)) {
        addFinding(
          filePath,
          tagType,
          `${kind}: replace broad JSDoc ${tag.tagName.getText()} type ${jsdocTagTypeText(sourceText, tagType)} with a structural type`
        );
      }
    }
  }
};

/**
 * Traverses AST nodes and validates JSDoc presence and type safety expectations.
 *
 * @param {string} filePath File path currently being validated.
 * @param {string} sourceText Parsed source text for that file.
 * @param {ts.Node} node AST node being visited.
 * @returns {void}
 */
const visit = function visit(filePath, sourceText, node) {
  if (isVariableLikeDeclaration(node)) {
    if (!hasJSDoc(node)) {
      addFunctionMissingTagFinding(filePath, node, "jsdoc");
    }
    ensureJSDocTypeSafety(filePath, sourceText, node, "Variable JSDoc");
  }

  if (
    isFunctionLikeForJSDoc(node) &&
    (isTopLevelFunction(node) || isClassMethod(node))
  ) {
    if (!hasJSDoc(node)) {
      addFunctionMissingTagFinding(filePath, node, "jsdoc");
    }
    ensureJSDocTypeSafety(filePath, sourceText, node, "Function JSDoc");
  }

  ts.forEachChild(node, (child) => {
    visit(filePath, sourceText, child);
  });
};

for (const filePath of sourceFiles) {
  const sourceText = fs.readFileSync(filePath, "utf-8");
  const sourceFile = ts.createSourceFile(
    filePath,
    sourceText,
    ts.ScriptTarget.ESNext,
    true,
    getScriptKind(filePath)
  );
  visit(filePath, sourceText, sourceFile);
}

if (findings.length > 0) {
  findings.sort((left, right) => {
    const fileCompare = left.file.localeCompare(right.file);
    if (fileCompare !== 0) {
      return fileCompare;
    }
    if (left.line !== right.line) {
      return left.line - right.line;
    }
    return left.column - right.column;
  });

  for (const finding of findings) {
    console.error(
      `${finding.file}:${finding.line}:${finding.column} [ERROR] ${finding.message}`
    );
  }
  process.exit(1);
}
