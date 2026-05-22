# 2026-05-22-manifest-driven-validator

- Status: active
- Created: 2026-05-22
- Last Updated: 2026-05-23
- Completed:
- Author: ririnto
- Assignee: ririnto

## Goal

`docs/harness/manifest.json`을 검증의 single source of truth로 사용하고, 4 stack validator(Kotlin/Java/Python/TypeScript)를 manifest-driven + add-on architecture + 일관된 코드 스타일로 정비한다. 추가 사용자 지침을 동일 plan에서 처리한다:

- WORKFLOW.md를 target project root에 배치
- PLANS.md를 "프로젝트 개발의 전체 계획" 의미로 강화
- CLAUDE.md를 canonical로, AGENTS.md/`.agents/`를 symlink로 통일
- OpenAI Harness Engineering 글을 reference로 plugin payload에 포함하고 docs/ template을 그 패턴에 맞게 강화
- `plugins/harness/skills/harness-install/` 구조 정비: `templates/` → `assets/`, depth 평탄화, gradle 모듈을 `buildSrc/`로, 다른 stack도 유사한 1-depth 형태로 통일

harness는 versioning하지 않으며, manifest 자체가 self-documenting 문서가 된다.

## Non-Goals

- 검증 *대상*의 의미적 변경
- `.claude/agents/`, `.claude/skills/` 의미적 구조 변경(파일 위치만 평탄화 대상)
- 추가 stack adapter는 Phase 11에서만

## Plan Convention

- Phase: 순차 실행 단위. 앞 phase 종료 후 다음 phase 시작.
- Task: 같은 phase 내에서 *독립적·병렬 안전*한 작업 단위. 같은 파일/디렉토리에 동시 쓰기 충돌이 없도록 분할. 서브에이전트 병렬 호출로 실행.

## Style Policy (모든 stack에 적용)

- Severity는 manifest의 카테고리별 `severity`만 사용. hardcoded `Severity.ERROR/WARN/INFO`는 manifest 로딩 실패 fallback 1건 외 0건.
- `mutableListOf`/`MutableList`/`ArrayList`/`HashMap` / 명령형 list 누적 금지. functional `buildList`/`Stream.toList()`/list comprehension/`.filter().map()` 사용.
- early/mid return 금지. single-exit. shell의 `return 1` fail-fast helper는 예외.
- silent failure 금지. catch 블록은 예외를 `Finding`으로 변환.
- Kotlin: `else`로 끝나는 if는 `when`. `Regex(...)` → `"...".toRegex()`. `kotlin.io.path` 우선.
- TS: backtick template literal.
- finding dedup은 `buildSet`/`LinkedHashSet`/`dict.fromkeys`/`new Set`.

## Phases

### [x] Phase 1: Manifest schema base

- [x] Task 1.1 — manifest.json 새 필드 도입
- [x] Task 1.2 — 미정의 필드 무시 정책 명시

### [x] Phase 2: Self-documenting manifest schema

- [x] Task 2.1 — 모든 check add-on을 `{description, enabled, severity, messages, parameters}` 통일 shape으로 정의
- [x] Task 2.2 — metadata 분리
- [x] Task 2.3 — manifest.schema.json (JSON Schema 2020-12) 작성

### [x] Phase 3: 4 stack validator 통합 마이그레이션 (병렬)

- [x] Task 3.1 — Kotlin
- [x] Task 3.2 — Java
- [x] Task 3.3 — Python
- [x] Task 3.4 — TypeScript

### [x] Phase 3.5: HarnessCheck enum 별도 파일 (병렬)

- [x] Task 3.5.1 — Kotlin `HarnessCheck.kt`
- [x] Task 3.5.2 — Java `HarnessCheck.java`
- [x] Task 3.5.3 — Python `harness_check.py`
- [x] Task 3.5.4 — TypeScript `harness-check.ts`

### [x] Phase 4: AST/native parser 강화 (병렬)

- [x] Task 4.1 — Kotlin `kotlinx-serialization-json` + BOM + Version Catalog
- [x] Task 4.2 — Java Jackson 3.x (`tools.jackson:jackson-bom`)
- [x] Task 4.3 — Python `json.loads` / TS `JSON.parse` 유지

### [x] Phase 5: 1차 self-check + dry-run

- [x] Task 5.1 — `plugin-self-check.sh` PASS
- [x] Task 5.2 — `install-harness.sh --mode bun` dry-run PASS
- [x] Task 5.3 — 4 stack grep style audit
- [x] Task 5.4 — manifest description + messages 검증

### [x] Phase 6: CLAUDE.md canonical 통일 + reference 첨부 (병렬)

이 프로젝트는 CLAUDE.md를 canonical로 사용한다. AGENTS.md/`.agents`는 symlink. 각 task는 다른 파일을 다루므로 병렬 안전.

- [x] Task 6.0a — OpenAI Harness Engineering 영어 원문 reference 추가 (`templates/common/docs/references/openai-harness-engineering.md`) + 4개 이미지 mermaid 변환 + 다이어그램 title + OpenAI-style theme (검정 배경 / 라임 그린 강조 / stadium 노드)
- [x] Task 6.0b — Symphony SPEC reference 추가 (`templates/common/docs/references/symphony-spec.md`) + editor's note prepend
- [x] Task 6.1 — `templates/common/CLAUDE.md` canonical 통일 (CLAUDE.md primary, AGENTS.md symlink, `.agents → .claude` symlink 명시)
- [x] Task 6.2 — `templates/common/.claude/agents/*.md` "Read AGENTS.md" → "Read CLAUDE.md"
- [x] Task 6.3 — `templates/common/.claude/skills/*/SKILL.md` "Read AGENTS.md" → "Read CLAUDE.md"
- [x] Task 6.4 — `templates/common/docs/harness/README.md` 표에서 CLAUDE.md primary, AGENTS.md symlink alias로 표기
- [x] Task 6.5 — `templates/common/docs/harness/manifest.json` failureMessage CLAUDE.md 통일
- [x] Task 6.6 — `templates/common/docs/harness/templates/agent/AGENT.md.tmpl`, `templates/common/WORKFLOW.md` 참조 정비
- [x] Task 6.7 — `templates/common/docs/harness/templates/docs/exec-plan.md.tmpl`에 Plan Convention (phase=순차, task=병렬, blocked by 표현) 추가

### [x] Phase 7: docs/ template 강화 — OpenAI Harness Engineering 패턴 (병렬)

각 task는 다른 파일을 다루므로 병렬 안전.

- [x] Task 7.1 — `docs/PLANS.md` 재작성
- [x] Task 7.2 — `docs/DESIGN.md` (Domain Layering mermaid + Cross-cutting Providers + title)
- [x] Task 7.3 — `docs/../ARCHITECTURE.md` (Domain Map / Layering mermaid + title / Data Flow / External Integrations / Validation Surfaces)
- [x] Task 7.4 — `docs/PRODUCT_SENSE.md`
- [x] Task 7.5 — `docs/QUALITY_SCORE.md`
- [x] Task 7.6 — `docs/RELIABILITY.md`
- [x] Task 7.7 — `docs/SECURITY.md`
- [x] Task 7.8 — `docs/FRONTEND.md` ("노출 표면 전체" 의미로 재작성: UI + CLI + API + SDK + 웹훅 등)
- [x] Task 7.9 — `docs/design-docs/core-beliefs.md`
- [x] Task 7.10 — `docs/exec-plans/tech-debt-tracker.md`

### [x] Phase 7.5: docs/ 강화 후 manifest 동기화 + plugin self-check 예외 처리 (순차)

- [x] Task 7.5.1 — manifest.json의 `requireFilesExist.paths` 모두 등록 확인 (이미 존재)
- [x] Task 7.5.2 — `forbidScaffoldLeaks.parameters.scope.excludedSubtrees`에 `docs/references` 추가
- [x] Task 7.5.3 — plugin-self-check.sh의 `unresolved_template_tokens` + `template_marker_check` case 예외에 placeholder template 파일 추가 (`docs/{DESIGN,PLANS,FRONTEND,PRODUCT_SENSE,QUALITY_SCORE,RELIABILITY,SECURITY,..}`/`docs/design-docs/core-beliefs.md`/`docs/exec-plans/tech-debt-tracker.md`/`docs/product-specs/*.md`/`docs/references/*.md`/`docs/harness/templates/*`/`.github/workflows/harness.yml`/`.gitlab-ci.yml`)
- [x] Task 7.5.4 — `{{...}}` placeholder는 그대로 유지 (사용자 환경에서 채우는 의도)

### [x] Phase 7.6: `.tmpl` 확장자 제거 (순차)

`.tmpl`은 Go template 의미라 IDE 해석 오해를 부른다. 경로명에 `templates`가 있으므로 확장자 없이도 의도 전달이 충분하다.

- [x] Task 7.6.1 — `find ... -name "*.tmpl"` 전체에 대해 `git mv` 로 `.tmpl` 제거
- [x] Task 7.6.2 — `install-harness.sh`의 `.gitlab-ci.yml.tmpl`/`.github/workflows/harness.yml.tmpl` case 매칭을 `.tmpl` 없는 형태로 갱신
- [x] Task 7.6.3 — `plugin-self-check.sh` 의 `.tmpl` 의존 로직 제거(`grep -F -v '.tmpl'` 삭제, "outside .tmpl asset" 메시지 정리, require_text 경로 갱신)
- [x] Task 7.6.4 — reference-llms 템플릿을 `> [!NOTE]` attribution + editor's note 형태로 재작성

### [x] Phase 8: 디렉토리 rename (직렬, 단일 작업)

- [x] Task 8.1 — `git mv plugins/harness/skills/harness-install/templates plugins/harness/skills/harness-install/assets`

### [x] Phase 9: rename 후속 경로 참조 갱신 (병렬)

각 task는 다른 파일.

- [x] Task 9.1 — `install-harness.sh` `template_dir="$skill_dir/assets"` (변수 1줄만 변경)
- [x] Task 9.2 — `plugin-self-check.sh` 모든 `skills/harness-install/templates/` → `assets/` (require_file/require_text/case 패턴 모두)
- [x] Task 9.3 — `harness-install/SKILL.md` plugin-source `templates/` 참조 없음 확인
- [x] Task 9.4 — `plugins/harness/README.md` layout 다이어그램 + 'Packaged Scripts and Assets' rename

검증: self-check PASS, shellcheck PASS, install --mode bun dry-run 성공.

### [ ] Phase 10: 각 stack depth 평탄화 + buildSrc 이동 (병렬)

각 task는 다른 stack 디렉토리. 서로 독립.

- [ ] Task 10.1 — Gradle: `assets/gradle/docs/harness/gradle-plugin/` → `assets/gradle/buildSrc/`로 이동. 내부 구조(`src/main/kotlin/...`)는 buildSrc 자동 인식 형태로 정리. `settings.gradle.kts`는 buildSrc 내부에서는 trivial 또는 제거
- [ ] Task 10.2 — Maven: `assets/maven/docs/harness/maven-plugin/` → `assets/maven/harness-maven-plugin/`로 1-depth 이동. target 설치 시 root submodule로 배치
- [ ] Task 10.3 — uv: `assets/uv/docs/harness/uv/` 구조를 유지하되 `assets/uv/`를 1-depth 진입점으로 (필요시 `docs/harness/uv/` 그대로 사본 매핑은 install 스크립트에서 처리)
- [ ] Task 10.4 — bun: uv와 동일 형태 정비

### [ ] Phase 11: stack 평탄화 후속 install/validate 갱신 (직렬)

Phase 10의 새 경로를 install-harness.sh와 stack-specific install 로직에 반영. Gradle의 경우 `buildSrc/`를 target root에 복사하도록 변경(기존 `includeBuild`/`apply false` 제거).

- [ ] Task 11.1 — `install-harness.sh`의 stack copy 로직을 Phase 10 결과에 맞춰 갱신
- [ ] Task 11.2 — Gradle target root에서 buildSrc 자동 인식이 되도록 settings.gradle.kts patch 로직 단순화
- [ ] Task 11.3 — Gradle pre-commit/pre-push hook은 `gradlew harnessValidate` 그대로 동작 확인

### [ ] Phase 12: Shell stack adapter (병렬 가능 단계)

shell-only 프로젝트 stack 추가. Phase 8~11 구조 정비 후 동일한 1-depth 컨벤션으로.

- [ ] Task 12.1 — `assets/shell/docs/harness/shell/harness-validate.sh` 작성 (POSIX sh, manifest는 `python3 -c 'import json; ...'`로 파싱, add-on registry, ERROR-only fail, `[SEVERITY] message`)
- [ ] Task 12.2 — `assets/shell/.github/workflows/harness.yml.tmpl`
- [ ] Task 12.3 — `install-harness.sh`에 `shell` 모드 추가
- [ ] Task 12.4 — `detect-stack.sh`에 shell 감지
- [ ] Task 12.5 — manifest.json의 `requireHookCommand.allowedCommands.shell` + `requireHookStage.stages.shell` 추가
- [ ] Task 12.6 — plugin README + `harness-install/SKILL.md` Validation Adapters 표에 shell 추가

### [ ] Phase 13: 최종 self-check + 5 stack dry-run (직렬)

- [ ] Task 13.1 — `plugin-self-check.sh` PASS
- [ ] Task 13.2 — `install-harness.sh --mode gradle/maven/uv/bun/shell` dry-run 각각 `Harness validation passed`
- [ ] Task 13.3 — 4 stack grep style audit (severity literal/mutable list/`Regex(` 0건)

### [ ] Phase 14: Plan completion

- [ ] Task 14.1 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status `completed` + `Completed: yyyy-MM-dd` 기록

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator. severity 도입 후 `[ERROR]`/`[WARN]`/`[INFO]` prefix가 stderr에 출력되는지 확인.

## Rollback Criteria

- 스키마 변경이 target validator를 break시키면 `git revert`.
- AST/native parser 도입으로 install 시간이 현저히 증가하면 정규식 기반으로 부분 롤백.
- Phase 8/9/10/11 구조 정비가 install-harness.sh를 break시키면 `git revert` 후 단계 재시도.

## Completion

모든 task가 체크되면 본 plan을 `docs/exec-plans/completed/`로 이동하고 `Status: completed`, `Completed: yyyy-MM-dd`를 기록한다.
