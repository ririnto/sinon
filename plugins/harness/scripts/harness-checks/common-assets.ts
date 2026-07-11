// -*- coding: utf-8 -*-

import path from "node:path";

import { checkCommonAgentAssets } from "./common-agent-assets.js";
import { checkCommonDocumentAssets } from "./common-document-assets.js";

/** Validate common packaged Harness assets. */
export const checkCommonAssets = (root: string): void => {
  const common = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "common"
  );
  checkCommonAgentAssets(common);
  checkCommonDocumentAssets(common);
  console.error("[common assets] OK");
};
