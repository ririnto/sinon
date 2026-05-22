# 2026-05-22-harness-docs-reorganization

- Status: completed
- Created: 2026-05-22
- Completed: 2026-05-22
- Owner: ririnto

## Goal

`plugins/harness/`가 설치하는 모든 harness 자산을 `.claude/harness/`에서 `docs/harness/`로 이전하고, 보일러플레이트 중복과 하위→상위 cross-reference를 제거해서 여러 skill이 공유 접근 가능한 문서 중심 harness로 정비한다.

## Non-Goals

- `.claude/agents/`, `.claude/skills/` 자체의 위치 변경 (네이티브 경로 유지)
- 새 stack adapter 추가
- runtime 동작이 아닌 documentation hygiene 외 추가 기능 변경
- marketplace manifest 변경

## Phases

Phases run sequentially. Tasks within a phase MAY run in parallel and SHOULD fit a single subagent invocation each.

### Phase 1: Plan and template scaffolding

- [x] Task 1.1 — `exec-plan.md.tmpl`을 `yyyy-MM-dd-<slug>` 파일명, phase/task 구조, 완료 시 이동 정책을 명시한 새 템플릿으로 갱신 (subagent: main)
- [x] Task 1.2 — 본 plan 파일을 `docs/exec-plans/active/2026-05-22-harness-docs-reorganization.md`로 작성 (subagent: main)

### Phase 2: Move templates/common/.claude/harness/ to templates/common/docs/harness/

- [x] Task 2.1 — `git mv`로 디렉토리 트리 이동: `templates/common/.claude/harness/{manifest.json,evolution-log.md,README.md,templates,git-hooks}` → `templates/common/docs/harness/...` (subagent: main)
- [x] Task 2.2 — stack adapter 디렉토리 이동: `templates/{gradle,maven,uv,bun}/.claude/harness/<adapter>` → `templates/{gradle,maven,uv,bun}/docs/harness/<adapter>` (subagent: main)

### Phase 3: Update install-harness.sh path references

- [x] Task 3.1 — `install-harness.sh`의 모든 `.claude/harness/` 리터럴, `core.hooksPath` 정책, gradle `includeBuild`, maven `-f` 경로, uv/bun runner 경로를 `docs/harness/`로 갱신 (subagent: main)
- [x] Task 3.2 — `copy_tree`의 root-contract 스킵 패턴에서 git-hooks 경로 갱신 + pre-commit hook이 검증하는 `require_file` 경로 갱신 (subagent: main)

### Phase 4: Update manifest.json, AGENTS.md, CLAUDE.md, evolution-log.md

- [x] Task 4.1 — `docs/harness/manifest.json`의 `requiredFiles` git-hooks 경로 갱신 + `harnessEvolution.log` 경로 갱신 (subagent: main)
- [x] Task 4.2 — `templates/common/AGENTS.md`의 모든 `.claude/harness/...` invariants/structure 다이어그램을 `docs/harness/...`로 갱신 (subagent: main)
- [x] Task 4.3 — `templates/common/CLAUDE.md`의 cross-ref 문구를 `docs/harness/README.md`로 갱신 (subagent: main)

### Phase 4.5: Unify templates/common/{AGENTS.md,CLAUDE.md} via symlink

sinon 루트는 `AGENTS.md → CLAUDE.md` symlink (sinon CLAUDE.md에 명시된 canonical 규칙). plugin templates도 같은 패턴으로 통일했다. 추가로 두 헤더("# Repository Harness Contract" + "# Claude Code Entry Point")가 단일 h1으로 통합되고, "Claude Code" agent-specific 명시도 일반화("## Entry Point") 되었다.

- [x] Task 4.5.1 — `templates/common/CLAUDE.md`를 단일 canonical 합본(harness contract + entry-point) + 단일 h1로 재작성 (subagent: main)
- [x] Task 4.5.2 — `templates/common/AGENTS.md`를 `CLAUDE.md`의 symlink로 교체 (subagent: main)
- [x] Task 4.5.3 — `install-harness.sh`의 `ensure_shared_root_contract`가 두 template이 같은 본문(symlink)일 때 합본을 두 번 cat 하지 않도록 marker 재평가 추가 + marker 자체를 `## Entry Point`로 일반화 (subagent: main)

### Phase 5: Update stack adapter internals

각 stack의 validator는 (a) 경로 `docs/harness/`로 갱신, (b) 가변 컬렉션 미사용, (c) 검증 영역 분리, (d) `docs/exec-plans/completed/`에 미완료 `- [ ] ` task가 남아 있으면 fail, (e) manifest와 정합한 상수로 갱신했다.

- [x] Task 5.1 — Maven adapter: `pom.xml`/`HarnessValidateMojo.java`의 경로 갱신, `List.copyOf`/`Stream.toList`로 ArrayList 제거, 11개 inner validator class + HarnessFiles 유틸 분리, PlanCompletionValidator 추가 (subagent: general-purpose)
- [x] Task 5.2 — Gradle adapter: `HarnessValidationPlugin.kt` 경로/`buildList` 1차 갱신 (subagent: main)
- [x] Task 5.3 — uv adapter: `harness_validate.py` 경로 갱신, list → tuple, 12개 함수 분리, `validate_completed_plans` 추가 (subagent: general-purpose)
- [x] Task 5.4 — bun adapter: `harness-validate.ts` 경로 갱신, readonly array + spread/flatMap, 11개 함수 분리, `validateCompletedPlans` 추가 (subagent: general-purpose)

> Note. Gradle validator class 분리(Phase 5.2b 원안)와 4 stack validator의 manifest-driven 통합(Phase 5.9 원안)은 over-engineering 우려에 따라 별도 plan `2026-05-22-manifest-driven-validator.md`로 이관했다.

### Phase 6: Update SKILL.md and agent files

- [x] Task 6.1 — Plugin skills SKILL.md (`harness-install`, `harness-validate`, `harness-evolve`) 경로 갱신 (subagent: main, sed 일괄)
- [x] Task 6.2 — Plugin agents (`harness-architect`, `harness-reviewer`, `harness-validator`) 갱신 (subagent: main)
- [x] Task 6.3 — Installed-target skills (`harness-orchestrate`, `harness-review`, `harness-validate`) 갱신 (subagent: main)
- [x] Task 6.4 — Installed-target agents (`harness-implementation-agent`, `harness-orchestrator`, `harness-review-agent`) 갱신 (subagent: main)

### Phase 7: Update plugin README.md and plugin-self-check.sh

- [x] Task 7.1 — `plugins/harness/README.md`의 Required Repository Structure 다이어그램, Git Hooks 섹션, Validation Adapters 표, Layout 갱신 (subagent: main)
- [x] Task 7.2 — `scripts/plugin-self-check.sh`의 packaged/tracked 자산 경로 + 마커 검사 갱신 (subagent: main)

### Phase 8: Remove docs/ boilerplate and per-directory index.md duplication

- [x] Task 8.1 — `templates/common/docs/{DESIGN,FRONTEND,PLANS,PRODUCT_SENSE,QUALITY_SCORE,RELIABILITY,SECURITY}.md` 7개의 동일 보일러플레이트 제거, 자기 영역에 맞는 짧은 placeholder로 재작성 (subagent: main)
- [x] Task 8.2 — `templates/common/docs/design-docs/index.md`와 `templates/common/docs/product-specs/index.md` 제거 (subagent: main)
- [x] Task 8.3 — `templates/common/docs/exec-plans/tech-debt-tracker.md`를 자기 영역 본문 + 빈 표 헤더로 재작성. `templates/common/docs/design-docs/core-beliefs.md`와 `templates/common/docs/product-specs/new-user-onboarding.md`도 함께 차별화 (subagent: main)
- [x] Task 8.4 — `manifest.json`의 `requiredFiles`에서 제거된 index.md 항목 제거 (Phase 5 commit에서 선반영) (subagent: main)

### Phase 9: Make docs/references/ seeds practical without bundling third-party prose

- [x] Task 9.1 — `templates/common/docs/references/{nixpacks-llms.txt,design-system-reference-llms.txt,uv-llms.txt}` 제거 (subagent: main)
- [x] Task 9.2 — `templates/common/docs/references/README.md` 작성: 디렉토리 용도, plugin이 외부 본문을 packaging하지 않는 이유, 명명 규칙, 제거 기준 (subagent: main)
- [x] Task 9.3 — `manifest.json`의 `optionalSeedFiles`를 `docs/product-specs/new-user-onboarding.md` 단일 항목으로 축소 (Phase 5에서 선반영) (subagent: main)

### Phase 10: Remove downstream → upstream cross-references

- [x] Task 10.1 — `templates/common/docs/**/*.md`의 "## Validation Link" 섹션 일괄 제거 (Phase 8에서 본문 재작성과 함께 처리) (subagent: main)
- [x] Task 10.2 — `templates/common/docs/harness/templates/docs/{design-doc,product-spec,generated-artifact}.md.tmpl`과 `skill/reference.md.tmpl`의 "## Validation Link" 섹션 제거. agent/skill의 진입점 reference(상→하 방향)는 의도된 link이므로 유지 (subagent: main)

### Phase 11: Self-check and final validation

- [x] Task 11.1 — `sh plugins/harness/scripts/plugin-self-check.sh` PASS (subagent: main)
- [x] Task 11.2 — 임시 디렉토리에 `install-harness.sh --mode bun` dry-run; 결과 트리가 `docs/harness/...`로 생성됨을 확인하고 bun validator `Harness validation passed` 통과까지 검증. dry-run 중 발견된 pre-commit hook 첫 줄 빈 줄 버그(`write_new_pre_commit_hook` heredoc) 수정 (subagent: main)
- [x] Task 11.3 — 본 plan 파일을 `docs/exec-plans/completed/2026-05-22-harness-docs-reorganization.md`로 이동, `Status: completed` + `Completed: 2026-05-22` 기록 (subagent: main)

## Validation

Phase별로 plugin-self-check.sh 및 dry-run install + bun validator로 검증. Phase 11 dry-run에서 `Harness validation passed` 확인.

## Rollback Criteria

- plugin-self-check 실패 시 해당 phase의 commit을 `git revert`로 되돌리고 사유를 plan에 기록한다.
- installer dry-run에서 `docs/harness/` 트리가 만들어지지 않으면 Phase 3을 재수행한다.

## Completion

모든 task가 체크되었다. 본 plan을 `docs/exec-plans/active/`에서 `docs/exec-plans/completed/`로 이동한다. 후속 과제(manifest-driven validator)는 `2026-05-22-manifest-driven-validator.md`로 이관됐다.
