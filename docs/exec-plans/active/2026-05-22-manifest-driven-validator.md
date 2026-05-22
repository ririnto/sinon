# 2026-05-22-manifest-driven-validator

- Status: active
- Created: 2026-05-22
- Author: ririnto
- Assignee: ririnto

## Goal

각 stack validator(Kotlin/Java/Python/TypeScript)가 자체적으로 `REQUIRED_FILES`, `REQUIRED_DIRECTORIES`, `LEAK_PATTERNS`, `REQUIRED_DOC_HEADINGS`, `OPTIONAL_SEED_FILES`, `TEMPLATE_GROUPS`, `EXPECTED_VALIDATION_COMMAND`, active asset base 목록 등을 hardcode하는 구조를 제거하고, `docs/harness/manifest.json`이 single source of truth가 되도록 옮긴다. 4 stack adapter는 manifest를 읽어 단순 loop만 돌도록 슬림화한다.

## Non-Goals

- 검증 *로직*의 동작 의미 변경 (지금까지의 검사 항목과 동일한 의미를 유지)
- 새 검증 영역 추가
- `.claude/agents/`, `.claude/skills/` 구조 변경

## Background

- 직전 plan(`2026-05-22-harness-docs-reorganization`)에서 4 stack validator를 모두 `docs/harness/` 경로 기반 + immutable 패턴 + 함수/클래스 영역 분리로 재작성했지만, 검증 대상 리스트는 여전히 각 stack 코드에 hardcode되어 있다.
- 같은 리스트(예: `REQUIRED_FILES` 14개 항목, `LEAK_PATTERNS` 5개 정규식)가 4곳에 중복되어 drift 위험이 크다.
- 사용자가 "각 검증 대상이 분산되기보다 manifest.json에서 명확히 관리되는 편이 낫다"고 명시.
- Gradle validator를 영역별 inner class 10+개로 쪼개는 방향(원래 Phase 5.2b)도 over-engineering 우려가 있어 manifest-driven으로 통합하면서 함께 슬림화한다.

## Phases

### Phase 1: Manifest schema extension

- [ ] Task 1.1 — `templates/common/docs/harness/manifest.json` 스키마 확장: 기존 키 외에 `requiredDocHeadings`(string[]), `leakPatterns`(`[{pattern, label, flags}]`), `activeAssetBases`(string[]), `excludedActiveAssetSubtrees`(string[]), `requiredContentChecks`(`[{file, mustContain, failureMessage}]`), `expectedValidationCommands`(`{gradle, maven, uv, bun}`), `completedPlanDirectory`(string), `requiredAuthoredDocsFilter`(prefix/suffix or 명시적 리스트) 추가. `schemaVersion`을 `0.4.0`으로 올린다. (subagent: main)
- [ ] Task 1.2 — manifest의 새 필드가 누락된 stack adapter validator는 새 필드를 사용하지 않으면 자연스럽게 무시되도록 backwards-compat 정책 정함 (스키마 문서에 명시) (subagent: main)

### Phase 2: Gradle validator slimming

- [ ] Task 2.1 — `HarnessValidationPlugin.kt`의 companion object 상수 중 manifest에서 읽을 수 있는 것은 모두 제거하고 manifest 로더를 통해 채운다 (subagent: main)
- [ ] Task 2.2 — inner class 분리는 manifest field 단위(StructureValidator, DocsValidator, ContentValidator, AgentsValidator, SkillsValidator, TemplateValidator, ActiveAssetsValidator, HooksValidator, ShebangValidator, PlanCompletionValidator)로 단순 정리. 10+개 미만으로 유지하고, 매우 작은 검증은 같은 class에 합칠 수 있다. (subagent: main)

### Phase 3: Maven/uv/bun validator slimming

- [ ] Task 3.1 — `HarnessValidateMojo.java`의 11개 inner class를 manifest field 단위로 줄이고 hardcoded 상수를 manifest 로더로 대체 (subagent: general-purpose)
- [ ] Task 3.2 — `harness_validate.py`의 모듈 전역 tuple 상수 중 manifest 출처인 것은 모두 제거 (subagent: general-purpose)
- [ ] Task 3.3 — `harness-validate.ts`의 readonly array 상수도 동일하게 정리 (subagent: general-purpose)

### Phase 4: Self-check + dry-run validation

- [ ] Task 4.1 — `sh plugins/harness/scripts/plugin-self-check.sh` PASS 확인 (subagent: main)
- [ ] Task 4.2 — `install-harness.sh --mode bun` dry-run 후 bun validator PASS 확인. uv/Gradle/Maven은 stack 도구가 환경에 없으면 코드 검증만 수행. (subagent: main)
- [ ] Task 4.3 — 모든 validator에서 hardcoded list가 사라졌는지 grep으로 검증: `grep -nE "REQUIRED_FILES|REQUIRED_DIRECTORIES|LEAK_PATTERNS|OPTIONAL_SEED_FILES" plugins/harness/skills/harness-install/templates/{maven,uv,bun,gradle}/...` (subagent: main)

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator로 정합성을 확인한다.

## Rollback Criteria

- manifest 스키마 변경이 backward-incompat 한 방식으로 적용되어 기존 target이 break되면 schemaVersion bump을 되돌리고 새 키를 optional로 다시 도입.

## Completion

모든 task가 체크되면 `docs/exec-plans/active/2026-05-22-manifest-driven-validator.md`를 `docs/exec-plans/completed/`로 이동하고 `Status: completed`, `Completed: yyyy-MM-dd`를 기록한다.
