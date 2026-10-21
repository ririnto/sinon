import type {
  ArrowFunctionExpression,
  Function as OxcFunction,
  Node
} from "@oxc-project/types";

interface Comment {
  readonly start: number;
  readonly end: number;
  readonly type: string;
  readonly value: string;
}

interface RuleContext {
  readonly sourceCode: {
    readonly getAllComments: () => readonly Comment[];
    readonly getCommentsBefore: (node: Node) => readonly Comment[];
    readonly getCommentsInside: (node: Node) => readonly Comment[];
    readonly text: string;
  };
  readonly report: (diagnostic: {
    readonly messageId: string;
    readonly node: Node;
  }) => void;
}

type FunctionNode =
  | (OxcFunction & {
      readonly type: "FunctionDeclaration" | "FunctionExpression";
    })
  | ArrowFunctionExpression;

const isFunctionNode = (node: Node): node is FunctionNode =>
  node.type === "FunctionDeclaration" ||
  node.type === "FunctionExpression" ||
  node.type === "ArrowFunctionExpression";

/**
 * Recognized tool directive comments that must stay inline with the code they control.
 */
const toolDirectivePattern =
  /^\s*(?:@ts-(?:check|expect-error|ignore|nocheck)|(?:eslint|oxlint)-(?:disable(?:-line|-next-line)?|enable)|(?:oxfmt|prettier)-ignore|(?:c8|istanbul|v8) ignore)(?![\w-])/u;

const noInlineCommentsRule = {
  create(context: RuleContext) {
    const documentation = new Set<number>();
    const reportedOffsets = new Set<number>();
    const declaration = (node: Node): void => {
      let boundary = node.start;
      const comments = context.sourceCode.getCommentsBefore(node);
      for (let index = comments.length - 1; index >= 0; index -= 1) {
        const comment = comments[index];
        if (
          comment !== undefined &&
          comment.type === "Block" &&
          comment.value.startsWith("*") &&
          context.sourceCode.text.slice(comment.end, boundary).trim() === ""
        ) {
          documentation.add(comment.start);
          boundary = comment.start;
        }
      }
    };
    const inspect = (node: Node, comments: readonly Comment[]): void => {
      for (const { start, type, value } of comments) {
        if (
          type !== "Shebang" &&
          !documentation.has(start) &&
          !toolDirectivePattern.test(value) &&
          !/^\s*(?:[#@]__(?:PURE|NO_SIDE_EFFECTS)__|[#@]\s*source(?:Mapping)?URL=|@vite-ignore\b|webpack[A-Z]\w*\s*:|\/\s*<(?:reference|amd-module|amd-dependency)\b)/u.test(
            value
          ) &&
          !/^\s*!?\s*\*?\s*(?:@license\b|@preserve\b|SPDX-License-Identifier:|\bcopyright\b)/iu.test(
            value
          ) &&
          !reportedOffsets.has(start)
        ) {
          reportedOffsets.add(start);
          context.report({ messageId: "noInlineComments", node });
        }
      }
    };
    const inspectFunction = (node: Node): void => {
      if (isFunctionNode(node) && node.body?.type === "BlockStatement") {
        inspect(node, context.sourceCode.getCommentsInside(node.body));
      }
    };
    return {
      "ArrowFunctionExpression:exit": inspectFunction,
      "FunctionDeclaration, VariableDeclaration, ClassDeclaration, MethodDefinition, PropertyDefinition, TSInterfaceDeclaration, TSTypeAliasDeclaration, TSEnumDeclaration, TSDeclareFunction, TSMethodSignature, TSPropertySignature, ExportNamedDeclaration, ExportDefaultDeclaration":
        declaration,
      "FunctionDeclaration:exit": inspectFunction,
      "FunctionExpression:exit": inspectFunction,
      "Program:exit"(node: Node) {
        inspect(node, context.sourceCode.getAllComments());
      }
    };
  },
  meta: {
    docs: {
      description:
        "Allow declaration documentation and required directive or license comments."
    },
    messages: {
      noInlineComments:
        "Use declaration documentation instead of explanatory comments."
    },
    type: "problem"
  } as const
};

const plugin = {
  meta: { name: "style" },
  rules: { "no-inline-comments-in-functions": noInlineCommentsRule }
};

/**
 * Provide the native Oxlint plugin for inline-comment checks.
 */
export default plugin;
