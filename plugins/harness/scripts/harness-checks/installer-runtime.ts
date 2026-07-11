// -*- coding: utf-8 -*-

import {
  checkInstallationScenarios,
  requireInstalledAgentRouting
} from "./installer-runtime-installation.js";
import { checkModeScenarios } from "./installer-runtime-modes.js";
import { checkForcedReplacementPermissions } from "./installer-runtime-permissions.js";
import {
  checkRefreshOwnershipScenarios,
  prepareRefreshScenario
} from "./installer-runtime-refresh.js";
import { checkSafetyScenarios } from "./installer-runtime-safety.js";
import { withRuntimeFixture } from "./installer-runtime-support.js";
import {
  checkWorkflowCompositionScenario,
  checkWorkflowRefreshScenario
} from "./installer-runtime-workflow.js";

/** Run installer and install-record adversarial scenarios from a non-Git cache. */
export const checkInstallerRuntime = (harnessRoot: string): void => {
  withRuntimeFixture(harnessRoot, (fixture) => {
    checkInstallationScenarios(fixture);
    checkForcedReplacementPermissions(fixture);
    const refreshScenario = prepareRefreshScenario(
      fixture,
      requireInstalledAgentRouting
    );
    checkWorkflowRefreshScenario(fixture, refreshScenario.refreshTarget);
    checkRefreshOwnershipScenarios(fixture, refreshScenario);
    checkSafetyScenarios(fixture);
    checkModeScenarios(
      fixture,
      (context) => {
        checkWorkflowCompositionScenario(fixture, context);
      },
      requireInstalledAgentRouting
    );
    console.error("[installer runtime] OK");
  });
};
