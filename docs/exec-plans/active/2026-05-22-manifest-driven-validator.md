# 2026-05-22-manifest-driven-validator

- Status: active
- Created: 2026-05-22
- Author: ririnto
- Assignee: ririnto

## Goal

`docs/harness/manifest.json`을 검증의 single source of truth로 사용하고, 4 stack validator(Kotlin/Java/Python/TypeScript)를 manifest-driven + add-on architecture + 일관된 코드 스타일로 정비한다. harness는 versioning하지 않으며, manifest 자체가 self-documenting 문서가 된다.

## Non-Goals

- 검증 *대상*의 의미적 변경(어떤 파일/디렉토리/패턴을 검증할지는 동일하게 유지)
- `.claude/agents/`, `.claude/skills/` 구조 변경
- 새 stack adapter 추가

## Style Policy (모든 stack에 적용)

- Severity는 manifest의 카테고리별 `severity`만 사용한다. `DEFAULT_SEVERITY` 같은 매핑 객체나 hardcoded `Severity.ERROR/WARN/INFO` (또는 동등한 string)는 *manifest 로딩 실패 fallback 1건 외*에는 0건.
- `mutableListOf`/`MutableList`/`MutableSet`/`MutableMap` (Kotlin) / `ArrayList`/`HashMap` (Java) / 명령형 list 누적 (Python `[].append` chain / TS `arr.push`) 사용 금지. 함수형 `buildList { ... }` / `Stream.toList()` / list comprehension / `.filter().map()` 사용.
- `forEach { if (cond) add(...) }` 패턴은 `.filter(cond).map(...)` 후 `addAll(...)` 또는 spread로 대체한다.
- early/mid return 금지. 함수 본문은 single-exit. 조건 reverse + `when`/if-else로 구성. (shell의 `return 1`로 fail-fast하는 helper는 예외)
- `try { ... } catch { return "" }` 같은 silent failure 금지. catch 블록은 예외를 `Finding(severity, category, message)`로 변환해 호출자가 알 수 있도록 한다.
- `emptyList()`/`tuple()`/`[]` 반환을 silent 결과 표시로 사용하지 않는다. "결과 없음 + 경고"는 `(value, warnings)` 페어 또는 명시적 Result type으로 표현. *정상적으로 비어 있는* 데이터(예: `seedFiles.paths = []`)는 그대로 OK.
- 단일 사용 지역 변수는 inline. 표현식이 길어 가독성 해치면 유지.
- Kotlin: `else`로 끝나는 if문은 `when` 표현식으로. `Regex(...)` 생성은 `"...".toRegex()`로. `Java.io.File`/`java.nio.file.Files` 직접 사용보다 `kotlin.io.path`의 `Path`/`readText`/`isSymbolicLink`/`readSymbolicLink`/`walk` extension function을 우선 사용한다. `import kotlin.io.path.Path` 같은 import 사용.
- TypeScript: backtick template literal 일관 사용. `"a " + x + " b"` 같은 concat 금지.
- 함수/식별자 이름은 *기능/동작*과 일치하게 (예: `validateContentChecks` → `validateRequiredContent`, `walk` → `walkDirectory`, `safeFileOrWalk` → `collectFilesUnder`).
- finding dedup 같은 중복 제거는 `.distinct()` 대신 `buildSet { addAll(...) }` 또는 `LinkedHashSet`을 우선 사용한다 (순서를 유지하면서 중복 제거; 의도가 명확). Python은 `dict.fromkeys(...)`, TS는 `new Set(...)` 또는 `Array.from(new Map(...))`.

## Phases

### [x] Phase 1: Manifest schema base

- [x] Task 1.1 — manifest.json에 새 필드 도입: `requiredDocHeadings`, `requiredContentChecks`, `activeAssetBases`, `excludedActiveAssetSubtrees`, `activeAssetExtensions`, `leakPatterns`, `expectedValidationCommands`, `hookStages`, `completedPlanDirectory`, `unfinishedTaskPattern`, `envShebangBases`. (harness는 versioning하지 않으므로 schemaVersion 필드는 두지 않는다)
- [x] Task 1.2 — 미정의 필드는 validator가 무시한다는 정책 명시 (versioning/legacy/deprecated 표현은 사용하지 않는다)

### [x] Phase 2: Self-documenting manifest schema

각 옵션을 (a) check add-on(description + severity + failure message templates + 대상 경로/데이터) 또는 (b) metadata(description + data, severity 없음, validator는 읽기만)로 명확히 분리. AGENTS.md = CLAUDE.md symlink이므로 `requireDocContent`는 CLAUDE.md만 검증.

- [x] Task 2.1 — `requireFilesExist`, `requireDirectoriesExist`, `requireKeepfileInEmptyDirectories`, `requireTemplateGroups`, `requireDocHeadings`, `requireDocContent`, `requireAgentFrontmatter`, `requireSkillFrontmatter`, `forbidScaffoldLeaks`, `requireHookShebang`, `requireHookExecutable`, `requireHookGeneratedMarker`, `requireHookStage`, `requireHookCommand`, `requireCiCommandMatchesHook`, `requireEnvShebangUnder`, `forbidUncheckedTasksUnder`, `forbidUnsafeSymlinks` (check add-ons; description + severity + failureMessageTemplate(s) + 대상 데이터)
- [x] Task 2.2 — `seedFiles`, `generatedArtifacts`, `harnessEvolution`, `teamPatterns` (metadata; description + data, severity 없음)

### [ ] Phase 3: 4 stack validator 통합 마이그레이션 (병렬 sub-agent × 4)

각 sub-agent가 자기 stack의 validator를 *모든 정책*에 맞춰 한 번에 다시 작성. inline 변환·early return 제거·silent failure 제거·functional filter().map()·severity 매니페스트 조회 등 작은 변환을 같은 sub-agent 안에서 통합 처리해 호출 횟수를 줄인다.

각 task의 산출물은 *해당 stack 한 파일*. 4개 모두 병렬 위임.

- [ ] Task 3.1 — Kotlin `HarnessValidationPlugin.kt`: 새 manifest schema 마이그레이션 + Style Policy 일괄 (`MutableList` 0, if-else→when, Regex→toRegex(), kotlin.io.path 우선, 단일 사용 inline, string template, forEach+if→filter().map(), no early return, no silent failure) + add-on architecture(`HarnessCheck { val category; fun applies(manifest); fun validate(root, manifest): List<Finding> }` 인터페이스 + 각 check class + registry). 호출 시 manifest를 그대로 인자로 전달. (subagent: general-purpose)
- [ ] Task 3.2 — Java `HarnessValidateMojo.java`: 새 manifest schema 마이그레이션 + Style Policy(ArrayList 0, Stream functional, no silent failure, no early return, single return) + add-on architecture(`interface HarnessCheck { String category(); boolean applies(...); List<Finding> validate(...); }` + 각 check class + registry). (subagent: general-purpose)
- [ ] Task 3.3 — Python `harness_validate.py`: 새 manifest schema 마이그레이션 + Style Policy(tuple, NamedTuple, list comprehension, no silent failure, no early return) + add-on architecture(`Protocol` 또는 `@dataclass(frozen=True)` HarnessCheck + 각 check 함수 또는 클래스 + registry tuple). (subagent: general-purpose)
- [ ] Task 3.4 — TypeScript `harness-validate.ts`: 새 manifest schema 마이그레이션 + Style Policy(readonly arrays, spread, template literal, no silent failure, no early return) + add-on architecture(`interface HarnessCheck { category; applies; validate; }` + 각 check function + registry). bun helper 함수들이 module-level `manifest` closure에 의존하던 부분은 명시적 인자 전달로 정리. (subagent: general-purpose)

### [ ] Phase 4: AST/native parser 강화

현재 Kotlin/Java validator는 manifest JSON을 정규식으로 파싱한다. 이는 fragile하고 사용자가 명시적으로 AST 기반으로 옮길 것을 요구했다. Python/TS는 이미 native JSON parser 사용 중.

- [ ] Task 4.1 — Kotlin: `gradle-plugin/build.gradle.kts`에 `kotlinx-serialization-json` 의존성 추가 (`implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.x")` + `plugins { kotlin("plugin.serialization") }`). validator의 정규식 manifest parser를 `Json { ignoreUnknownKeys = true }.parseToJsonElement(text)` 기반으로 교체. 또는 의존성 부담이 큰 경우 Gradle 환경에서 항상 가능한 `groovy.json.JsonSlurper`를 사용. (subagent: general-purpose)
- [ ] Task 4.2 — Java: `maven-plugin/pom.xml`에 `com.fasterxml.jackson.core:jackson-databind` 의존성 추가. 정규식 manifest parser를 `ObjectMapper`로 교체. (subagent: general-purpose)
- [ ] Task 4.3 — markdown frontmatter 검사(`(?m)^name:\s*[-a-z0-9]+\s*$` 등)는 stack별 YAML parser로 교체 가능한지 검토. 의존성 부담이 크면 정규식 유지하되 `kotlin.io.path` / `pathlib` / `node:fs/promises` API로 reading만 갱신. (subagent: general-purpose)

### [ ] Phase 5: Self-check + dry-run 재검증

- [ ] Task 5.1 — `plugin-self-check.sh` PASS
- [ ] Task 5.2 — `install-harness.sh --mode bun` dry-run → bun validator 출력이 `[ERROR]`/`[WARN]`/`[INFO]` prefix를 갖는지 확인. ERROR 0건이면 `Harness validation passed` + exit 0
- [ ] Task 5.3 — 다음 grep 결과가 manifest 로딩 실패 fallback 1건 외 0건이어야 한다(4 stack 모두):
  - Kotlin: `Severity\.(ERROR|WARN|INFO)`, `MutableList`, `mutableListOf`, `Regex\(`, `\.startsWith\(` 같은 `java.io.File` 직접 호출
  - Java: `ArrayList`, `new HashMap`, `Severity` literal
  - Python: `Finding\("(ERROR|WARN|INFO)"`
  - TS: `severity: "(ERROR|WARN|INFO)"`
- [ ] Task 5.4 — manifest의 모든 카테고리에 description + (check면) failureMessageTemplate 명시 확인

### [ ] Phase 5.5: Shell stack adapter 추가 (gradle/maven/uv/bun 외 5번째)

shell-only 프로젝트(POSIX sh/bash 기반)에서도 harness를 사용할 수 있도록 stack adapter 추가. 다른 stack과 동일하게 manifest를 single source of truth로 읽고 동일한 add-on architecture로 동작.

- [ ] Task 5.5.1 — `templates/shell/docs/harness/shell/harness-validate.sh` 작성. POSIX sh 기반(set -e, JSDoc 함수 docstring, 파일별 검증 add-on 함수). manifest.json 파싱은 `python3 -c 'import json; ...'` 또는 `jq`가 있으면 jq 사용. add-on registry, ERROR-only fail, `[SEVERITY] message` 출력 형식 유지 (subagent: harness:harness-architect)
- [ ] Task 5.5.2 — `templates/shell/.github/workflows/harness.yml.tmpl`, `templates/shell/.gitlab-ci.yml.tmpl` 추가 (subagent: general-purpose)
- [ ] Task 5.5.3 — `scripts/install-harness.sh`에 `shell` 모드 추가. mode case 추가, `validation_command_for_mode` / `pre_commit_command_for_mode` / `pre_push_command_for_mode`에 shell 처리. (subagent: general-purpose)
- [ ] Task 5.5.4 — `scripts/detect-stack.sh`에 shell stack 감지 로직 추가. Makefile 또는 root-level `*.sh` 존재 + 다른 stack 부재로 추론 (subagent: general-purpose)
- [ ] Task 5.5.5 — manifest.json의 `requireHookCommand.allowedCommands.shell` + `requireHookStage.stages.shell` 추가. validator의 stack lookup이 자동으로 새 키를 읽도록 (manifest-driven이므로 코드 변경 불필요) (subagent: main)
- [ ] Task 5.5.6 — plugin README, `harness-install/SKILL.md`의 Validation Adapters 표에 shell 추가 (subagent: general-purpose)

### [ ] Phase 6: Gradle buildSrc 재배치 + assets/ 디렉토리 컨벤션 (별도 plan으로 분리 가능)

- [ ] Task 6.1 — `skills/harness-install/templates/` → `skills/harness-install/assets/` 재배치 (sinon plugin authoring 컨벤션). install-harness.sh가 새 위치를 가리키도록 갱신 (subagent: harness:harness-architect)
- [ ] Task 6.2 — Gradle adapter를 `gradle-plugin/` composite build에서 `buildSrc/`로 옮길 시 *target build에 미치는 영향* 분석 (buildSrc는 root project가 자동 인식). 적절한지 검증 후 결정 (subagent: harness:harness-architect)

### [ ] Phase 7: Plan completion

- [ ] Task 7.1 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status `completed` + `Completed: yyyy-MM-dd` 기록

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator. severity 도입 후에는 `[ERROR]`/`[WARN]`/`[INFO]` prefix가 stderr에 출력되는지 확인.

## Rollback Criteria

- manifest 스키마 변경이 target validator를 break시키면 그 commit을 `git revert`로 되돌린다. harness는 versioning하지 않으므로 "schema bump을 되돌린다" 같은 개념이 없고 — 단지 *현재 committed 상태가 진실*이다.
- AST/native parser 도입으로 인해 stack 의존성이 늘어 install 시간이 현저히 증가하면 정규식 기반으로 부분 롤백 가능 (Phase 4 task별 분리).

## Completion

모든 task가 체크되면 본 plan을 `docs/exec-plans/completed/`로 이동하고 `Status: completed`, `Completed: yyyy-MM-dd`를 기록한다.
