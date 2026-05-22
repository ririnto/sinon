# 2026-05-22-harness-docs-reorganization

Status: active
Created: 2026-05-22
Owner: ririnto

## Goal

`plugins/harness/`가 설치하는 모든 harness 자산을 `.claude/harness/`에서 `docs/harness/`로 이전하고, 보일러플레이트 중복과 하위→상위 cross-reference를 제거해서 여러 skill이 공유 접근 가능한 문서 중심 harness로 정비한다.

## Non-Goals

- `.claude/agents/`, `.claude/skills/` 자체의 위치 변경 (Claude Code 네이티브 경로 유지)
- 새 stack adapter 추가
- runtime 동작이 아닌 documentation hygiene 외 추가 기능 변경
- marketplace manifest 변경

## Phases

Phases run sequentially. Tasks within a phase MAY run in parallel and SHOULD fit a single subagent invocation each.

### Phase 1: Plan and template scaffolding

- [x] Task 1.1 — `exec-plan.md.tmpl`을 `yyyy-MM-dd-<slug>` 파일명, phase/task 구조, 완료 시 이동 정책을 명시한 새 템플릿으로 갱신 (subagent: main)
- [x] Task 1.2 — 본 plan 파일을 `docs/exec-plans/active/2026-05-22-harness-docs-reorganization.md`로 작성 (subagent: main)

### Phase 2: Move templates/common/.claude/harness/ to templates/common/docs/harness/

- [ ] Task 2.1 — `git mv`로 디렉토리 트리 이동: `templates/common/.claude/harness/{manifest.json,evolution-log.md,README.md,templates,git-hooks}` → `templates/common/docs/harness/...` (subagent: main)
- [ ] Task 2.2 — stack adapter 디렉토리 이동: `templates/{gradle,maven,uv,bun}/.claude/harness/<adapter>` → `templates/{gradle,maven,uv,bun}/docs/harness/<adapter>` (subagent: main)

### Phase 3: Update install-harness.sh path references

- [ ] Task 3.1 — `install-harness.sh`의 모든 `.claude/harness/` 리터럴, `core.hooksPath` 정책, gradle `includeBuild`, maven `-f` 경로, uv/bun runner 경로를 `docs/harness/`로 갱신 (subagent: main)
- [ ] Task 3.2 — `copy_tree`의 root-contract 스킵 패턴에서 git-hooks 경로 갱신 + pre-commit hook이 검증하는 `require_file` 경로 갱신 (subagent: main)

### Phase 4: Update manifest.json, AGENTS.md, CLAUDE.md, evolution-log.md

- [ ] Task 4.1 — `docs/harness/manifest.json`의 `requiredFiles` git-hooks 경로 갱신 + `harnessEvolution.log` 경로 갱신 (subagent: main)
- [ ] Task 4.2 — `templates/common/AGENTS.md`의 모든 `.claude/harness/...` invariants/structure 다이어그램을 `docs/harness/...`로 갱신 (subagent: main)
- [ ] Task 4.3 — `templates/common/CLAUDE.md`의 cross-ref 문구를 `docs/harness/README.md`로 갱신 (subagent: main)

### Phase 5: Update stack adapter internals

- [ ] Task 5.1 — Maven adapter: `pom.xml`의 모듈/플러그인 경로, `HarnessValidateMojo.java`의 default path 상수 갱신 (subagent: general-purpose)
- [ ] Task 5.2 — Gradle adapter: `HarnessValidationPlugin.kt`의 default path 상수, `settings.gradle.kts` 샘플의 `includeBuild` 갱신 (subagent: general-purpose)
- [ ] Task 5.3 — uv adapter: `harness_validate.py`의 default manifest path 갱신 + `uv/README.md` 사용 예시 갱신 (subagent: general-purpose)
- [ ] Task 5.4 — bun adapter: `harness-validate.ts`의 default manifest path 갱신 + `bun/README.md` 사용 예시 갱신 (subagent: general-purpose)

### Phase 6: Update SKILL.md and agent files

- [ ] Task 6.1 — Plugin skills (`harness-install`, `harness-validate`, `harness-evolve`) SKILL.md의 모든 `.claude/harness/` 참조 갱신 (subagent: general-purpose)
- [ ] Task 6.2 — Plugin agents (`harness-architect`, `harness-reviewer`, `harness-validator`) 갱신 (subagent: general-purpose)
- [ ] Task 6.3 — Installed-target skills (`harness-orchestrate`, `harness-review`, `harness-validate`) 갱신 (subagent: general-purpose)
- [ ] Task 6.4 — Installed-target agents (`harness-implementation-agent`, `harness-orchestrator`, `harness-review-agent`) 갱신 (subagent: general-purpose)

### Phase 7: Update plugin README.md and plugin-self-check.sh

- [ ] Task 7.1 — `plugins/harness/README.md`의 Required Repository Structure 다이어그램, Git Hooks 섹션, Validation Adapters 표, Target Ownership 텍스트 갱신 (subagent: main)
- [ ] Task 7.2 — `scripts/plugin-self-check.sh`의 packaged/tracked 자산 경로 갱신 (subagent: main)

### Phase 8: Remove docs/ boilerplate and per-directory index.md duplication

- [ ] Task 8.1 — `templates/common/docs/{DESIGN,FRONTEND,PLANS,PRODUCT_SENSE,QUALITY_SCORE,RELIABILITY,SECURITY}.md` 7개 파일의 동일 보일러플레이트 제거, 자기 영역 placeholder 한두 줄로 재작성 (subagent: main)
- [ ] Task 8.2 — `templates/common/docs/design-docs/index.md`와 `templates/common/docs/product-specs/index.md` 제거 (subagent: main)
- [ ] Task 8.3 — `templates/common/docs/exec-plans/tech-debt-tracker.md`의 동일 보일러플레이트를 자기 영역에 맞는 텍스트로 재작성 (subagent: main)
- [ ] Task 8.4 — `manifest.json`의 `requiredFiles`에서 제거된 index.md 항목 제거 (subagent: main)

### Phase 9: Make docs/references/ seeds practical without bundling third-party prose

플러그인은 외부 자료의 본문을 install payload에 포함하면 안 된다. 저작권/배포 위험과 plugin payload 비대화가 모두 부작용이다. references/에는 짧은 placeholder seed만 두고, 본문은 target 프로젝트가 직접 채운다.

- [ ] Task 9.1 — `templates/common/docs/references/{nixpacks-llms.txt,design-system-reference-llms.txt,uv-llms.txt}` 제거 (subagent: main)
- [ ] Task 9.2 — `templates/common/docs/references/README.md`(또는 `.gitkeep`)에 references/ 디렉토리의 용도, 명명 규칙, 출처/freshness 기록 정책만 적은 placeholder seed 작성 (subagent: main). 본문 cache는 포함하지 않는다. 예시 후보 URL은 OpenAI Harness Engineering 글과 openai/symphony SPEC.md 같은 외부 자료지만 본문은 plugin에 두지 않는다.
- [ ] Task 9.3 — `manifest.json`의 `optionalSeedFiles` 갱신: 제거된 *-llms.txt 항목 제거, references/ 자체는 `.gitkeep` 또는 README 형태의 placeholder seed로 추적 (subagent: main)

### Phase 10: Remove downstream → upstream cross-references

- [ ] Task 10.1 — `templates/common/docs/**/*.md`에서 "Run the stack-specific harness validation command listed in `.claude/harness/README.md`" 등 하위 문서에서 상위 harness 진입점을 참조하는 cross-ref 제거 (subagent: general-purpose)
- [ ] Task 10.2 — `docs/harness/README.md`는 상위 진입점이므로 다른 docs/* 로의 하향 link만 유지 (subagent: main)

### Phase 11: Self-check and final validation

- [ ] Task 11.1 — `sh plugins/harness/scripts/plugin-self-check.sh` 실행 (subagent: main)
- [ ] Task 11.2 — 임시 디렉토리에 `install-harness.sh` dry-run 후 결과 트리가 `docs/harness/...`인지, gitkeep/manifest 정합성 확인 (subagent: main)
- [ ] Task 11.3 — 본 plan 파일을 `docs/exec-plans/completed/2026-05-22-harness-docs-reorganization.md`로 이동, `Status: completed` 변경, `Completed: yyyy-MM-dd` 추가 (subagent: main)

## Validation

Phase별로 가능한 가벼운 검증을 수행하고, Phase 11에서 전체 검증을 수행한다. 각 phase 종료 시 `git status`, `git diff --stat`, 필요 시 `sh plugins/harness/scripts/plugin-self-check.sh`를 실행한다.

## Rollback Criteria

- 어떤 phase 결과로 plugin-self-check가 실패하고 root cause를 phase 안에서 해결할 수 없으면 해당 phase의 commit을 `git revert`로 되돌리고 plan에 사유를 기록한다.
- installer dry-run에서 `docs/harness/` 트리가 만들어지지 않으면 phase 3을 다시 실행한다.

## Completion

When every task is checked, move this file from `docs/exec-plans/active/` to `docs/exec-plans/completed/` without renaming, change `Status: active` to `Status: completed`, and append `Completed: yyyy-MM-dd`.
