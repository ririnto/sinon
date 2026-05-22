# 2026-05-23-uv-bun-flatten-and-shell-stack

- Status: completed
- Created: 2026-05-23
- Last Updated: 2026-05-23
- Completed: 2026-05-23
- Author: ririnto
- Assignee: ririnto

## Goal

(1) uv/bun stack의 plugin-side depth 평탄화 — `assets/<stack>/docs/harness/<stack>/` (4-depth) → `assets/<stack>/runtime/` (2-depth). target 측의 `docs/harness/<stack>/`는 그대로 유지. (2) shell stack adapter 신규 추가. Phase 12에서 deferred되었던 항목을 처리.

## Phases

### [x] Phase 1: uv/bun depth 평탄화

- [x] Task 1.1 — `assets/uv/docs/harness/uv/` → `assets/uv/runtime/` git mv
- [x] Task 1.2 — `assets/bun/docs/harness/bun/` → `assets/bun/runtime/` git mv
- [x] Task 1.3 — `install-harness.sh` `copy_stack_tree`에 `runtime/*` → `docs/harness/$mode/` 매핑 추가
- [x] Task 1.4 — `plugin-self-check.sh` require_file 경로 갱신

### [x] Phase 2: Shell stack adapter

- [x] Task 2.1 — `install-harness.sh` mode case에 `shell` 추가; `validation_command_for_mode` / `pre_commit_command_for_mode` / `pre_push_command_for_mode`에 shell 처리
- [x] Task 2.2 — `detect-stack.sh` shell 감지(Makefile 또는 root-level `*.sh`) 추가
- [x] Task 2.3 — `assets/shell/runtime/harness-validate.sh` POSIX sh validator 작성 (manifest는 python3로 파싱; 7개 핵심 add-on 구현)
- [x] Task 2.4 — `assets/shell/.github/workflows/harness.yml`, `assets/shell/.gitlab-ci.yml` (CI에서 python3 설치 + validation_command 실행)
- [x] Task 2.5 — `assets/shell/runtime/README.md`
- [x] Task 2.6 — manifest.json에 `requireHookStage.stages.shell` + `requireHookCommand.allowedCommands.shell` 추가
- [x] Task 2.7 — `plugin-self-check.sh` require_file에 shell stack assets 추가
- [x] Task 2.8 — plugin README + harness-install/SKILL.md adapter 표 + narrative + mode list에 shell 추가

### [x] Phase 3: 검증

- [x] Task 3.1 — `plugin-self-check.sh` PASS
- [x] Task 3.2 — `shellcheck` PASS (install-harness.sh + plugin-self-check.sh + detect-stack.sh + shell harness-validate.sh)
- [x] Task 3.3 — `install --mode uv/bun/shell` dry-run 모두 성공; target/docs/harness/<stack>/ layout 정상

## Validation

`plugin-self-check.sh PASS`, `shellcheck PASS`, 5 stack(gradle/maven/uv/bun/shell) install dry-run 정상.

## Completion

본 plan은 직접 completed/로 작성되었으며 active/를 거치지 않는다. follow-up 성격 + 단일 commit으로 완결되는 작업이기 때문에 history 기록 용도로만 보관.
