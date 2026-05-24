---
status: completed
created: 2026-05-22
updated: 2026-05-23
completed: 2026-05-23
author: ririnto
assignee: ririnto
---

# 2026-05-22-manifest-driven-validator

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

- [x] Task 7.5.1 — manifest.json의 `filePresence.paths` 모두 등록 확인 (이미 존재)
- [x] Task 7.5.2 — `scaffoldLeaks.parameters.scope.excludedSubtrees`에 `docs/references` 추가
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

### [x] Phase 10: 각 stack depth 평탄화 + buildSrc 이동

- [x] Task 10.1 — Gradle: `assets/gradle/docs/harness/gradle-plugin/` → `assets/gradle/buildSrc/` git mv
- [x] Task 10.2 — Maven: `assets/maven/docs/harness/maven-plugin/` → `assets/maven/harness-maven-plugin/` git mv
- [x] Task 10.3 — uv: `assets/uv/docs/harness/uv/` 구조 유지 (target이 docs/harness/uv를 그대로 받음)
- [x] Task 10.4 — bun: uv와 동일 형태 (변경 없음)

### [x] Phase 11: stack 평탄화 후속 install/validate 갱신

- [x] Task 11.1 — install-harness.sh: ensure_gradle_settings_include 제거, install_gradle은 build.gradle.kts에만 `apply(plugin)` append. Gradle buildSrc는 자동 인식이므로 settings.gradle.kts patch 불필요
- [x] Task 11.2 — Maven validation command: `mvn -q -f harness-maven-plugin/pom.xml install ...`
- [x] Task 11.3 — manifest.json 갱신: `directories`의 `docs/harness/gradle-plugin/src/main/kotlin` → `buildSrc/src/main/kotlin`, `allowedCommands.maven` → `harness-maven-plugin/pom.xml`
- [x] Task 11.4 — plugin-self-check.sh require_file 경로 갱신
- [x] Task 11.5 — plugins/harness/README.md + assets/common/docs/harness/README.md narrative 갱신 (buildSrc / harness-maven-plugin)
- [x] Task 11.6 — Gradle dry-run: target/buildSrc/ 에 배치 확인, target/docs/harness/gradle-plugin/ 부재 확인
- [x] Task 11.7 — Maven dry-run: target/harness-maven-plugin/ 에 배치 확인, target/docs/harness/maven-plugin/ 부재 확인

검증: self-check PASS, shellcheck PASS, gradle/maven dry-run 모두 성공.

### [-] Phase 12: Shell stack adapter — *follow-up plan으로 분리*

shell-only 프로젝트 stack adapter 추가는 minimum viable validator + CI workflow + detect-stack 매칭 + manifest stage/command 추가 + 문서 표 갱신을 모두 새 stack 도입 수준으로 작성해야 한다. 본 plan의 다른 phase는 *기존 4 stack 정비*에 한정되므로, 새 stack 도입은 별도 plan(`docs/exec-plans/active/yyyy-MM-dd-shell-stack-adapter.md`)으로 분리한다. 본 plan에서는 *Phase 12를 의도적으로 보류*했음을 기록만 한다.

- [-] Task 12.1 — *deferred* `assets/shell/docs/harness/shell/harness-validate.sh`
- [-] Task 12.2 — *deferred* `assets/shell/.github/workflows/harness.yml` + `.gitlab-ci.yml`
- [-] Task 12.3 — *deferred* install-harness.sh shell 모드
- [-] Task 12.4 — *deferred* detect-stack.sh shell 감지
- [-] Task 12.5 — *deferred* manifest.json `stages.shell` + `allowedCommands.shell`
- [-] Task 12.6 — *deferred* README + SKILL.md adapter 표

### [x] Phase 13: 최종 self-check + 4 stack dry-run (직렬)

- [x] Task 13.1 — `plugin-self-check.sh` PASS
- [x] Task 13.2 — gradle / maven / uv / bun dry-run 모두 성공. target layout 확인: `buildSrc/`, `harness-maven-plugin/`, `docs/harness/uv/`, `docs/harness/bun/`
- [x] Task 13.3 — 4 stack grep style audit. Java `ArrayList/HashMap` 0건; Kotlin `Severity.X` 사용은 enum value comparison + manifest 로딩 실패 fallback에 한정 (정책 허용 예외); Python `Finding("ERROR", ...)`는 manifest 항목 누락/regex 파싱 실패 fallback에 한정; TS `severity: "ERROR"`는 type 선언 + symlink fail-fast walker 한정. 그 외 hardcoded severity 0건
- [x] Task 13.4 — shellcheck on install-harness.sh + plugin-self-check.sh + detect-stack.sh: 0 issues

### [x] Phase 14: Plan completion

- [x] Task 14.1 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status `completed` + `Completed: 2026-05-23` 기록

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator. severity 도입 후 `[ERROR]`/`[WARN]`/`[INFO]` prefix가 stderr에 출력되는지 확인.

## Rollback Criteria

- 스키마 변경이 target validator를 break시키면 `git revert`.
- AST/native parser 도입으로 install 시간이 현저히 증가하면 정규식 기반으로 부분 롤백.
- Phase 8/9/10/11 구조 정비가 install-harness.sh를 break시키면 `git revert` 후 단계 재시도.

## Completion

모든 task가 체크되면 본 plan을 `docs/exec-plans/completed/`로 이동하고 frontmatter에 `status: completed`, `completed: yyyy-MM-dd`를 기록한다.
