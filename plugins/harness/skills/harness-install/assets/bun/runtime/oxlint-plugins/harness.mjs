/**
 * oxlint custom plugin implementing the six harness TypeScript conventions that
 * have no built-in oxlint equivalent. Loaded by oxlint via Node as an ESM module.
 */

/**
 * Walk a node's descendant AST nodes, invoking the visitor on each. The
 * `parent` key is skipped so the walk never climbs back up the tree.
 *
 * @param {object} node The AST node to start from.
 * @param {(child: object) => void} visit Callback invoked for each descendant node.
 */
function walkChildren(node, visit) {
    for (const [key, value] of Object.entries(node)) {
        if (key === "parent") {
            continue;
        }
        if (Array.isArray(value)) {
            for (const item of value) {
                if (item && typeof item === "object" && typeof item.type === "string") {
                    visit(item);
                    walkChildren(item, visit);
                }
            }
        } else if (value && typeof value === "object" && typeof value.type === "string") {
            visit(value);
            walkChildren(value, visit);
        }
    }
}

/**
 * Return the dotted identifier path for a member-access receiver, or "" when the
 * receiver is not a pure identifier/dot-member chain.
 *
 * @param {object} node The receiver expression node.
 * @returns {string} The dotted path such as "this.logger" or "console".
 */
function dottedReceiver(node) {
    if (node.type === "Identifier") {
        return node.name;
    }
    if (node.type === "MemberExpression" && node.computed === false && node.property.type === "Identifier") {
        const base = dottedReceiver(node.object);
        return base ? `${base}.${node.property.name}` : node.property.name;
    }
    return "";
}

/**
 * Determine whether a call expression is a recognized logging call: a bare
 * `logger(...)`/`log(...)`, or a method call whose receiver is `console`,
 * `logger`, `log`, or any dot-chain ending in `.logger`/`.log`.
 *
 * @param {object} node A CallExpression node.
 * @returns {boolean} True when the call is treated as logging.
 */
function isLoggingCall(node) {
    if (node.type !== "CallExpression") {
        return false;
    }
    const callee = node.callee;
    if (callee.type === "Identifier") {
        return callee.name === "logger" || callee.name === "log";
    }
    if (callee.type !== "MemberExpression") {
        return false;
    }
    const receiver = dottedReceiver(callee.object);
    return receiver === "console" || receiver === "logger" || receiver === "log" || receiver.endsWith(".logger") || receiver.endsWith(".log");
}

/**
 * Return a human-readable name for a declaration node.
 *
 * @param {object} declaration A declaration node.
 * @returns {string} The declared name, or "<unknown>".
 */
function declarationName(declaration) {
    if (declaration.id && declaration.id.name) {
        return declaration.id.name;
    }
    if (declaration.type === "VariableDeclaration" && declaration.declarations.length > 0) {
        const first = declaration.declarations[0];
        if (first.id && first.id.name) {
            return first.id.name;
        }
    }
    return "<unknown>";
}

export default {
    meta: { name: "harness" },
    rules: {
        greaterThanComparison: {
            meta: { fixable: "code" },
            create(context) {
                return {
                    BinaryExpression(node) {
                        if (node.operator === ">" || node.operator === ">=") {
                            const inverse = node.operator === ">" ? "<" : "<=";
                            context.report({
                                node,
                                message: `forbidden \`${node.operator}\` comparison; use \`${inverse}\` with operands flipped`,
                                fix(fixer) {
                                    const left = context.sourceCode.getText(node.left);
                                    const right = context.sourceCode.getText(node.right);
                                    return fixer.replaceText(node, `${right} ${inverse} ${left}`);
                                },
                            });
                        }
                    },
                };
            },
        },
        multilineDocStyle: {
            meta: { fixable: "code" },
            create(context) {
                return {
                    "Program:exit"() {
                        for (const comment of context.sourceCode.getAllComments()) {
                            if (comment.type === "Block" && comment.value.startsWith("*") && !comment.value.includes("\n")) {
                                context.report({
                                    loc: comment.loc,
                                    message: "TSDoc comment must use multiline style",
                                    fix(fixer) {
                                        const inner = comment.value.replace(/^\*+/, "").trim();
                                        return fixer.replaceTextRange(comment.range, `/**\n * ${inner}\n */`);
                                    },
                                });
                            }
                        }
                    },
                };
            },
        },
        publicDeclarationDocComment: {
            meta: {},
            create(context) {
                const documentedKinds = ["FunctionDeclaration", "ClassDeclaration", "TSInterfaceDeclaration", "TSTypeAliasDeclaration", "VariableDeclaration"];
                return {
                    ExportNamedDeclaration(node) {
                        const declaration = node.declaration;
                        if (!declaration || !documentedKinds.includes(declaration.type)) {
                            return;
                        }
                        const before = context.sourceCode.getCommentsBefore(node);
                        const hasDoc = before.some((comment) => comment.type === "Block" && comment.value.startsWith("*"));
                        if (!hasDoc) {
                            context.report({
                                node: declaration,
                                message: `public declaration \`${declarationName(declaration)}\` is missing a documentation comment`,
                            });
                        }
                    },
                };
            },
        },
    },
};
