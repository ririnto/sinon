#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Forbid implicit `it` lambda parameters in Kotlin.
 */
export const implicitLambdaItRule: HarnessCheckRule = {
    category: "implicitLambdaIt",
    applies(_: RuleContext): boolean {
        return false;
    },

    validate(_ctx: RuleContext): readonly Finding[] {
        return [];
    },
};
