# 2026-05-22-manifest-driven-validator

- Status: active
- Created: 2026-05-22
- Author: ririnto
- Assignee: ririnto

## Goal

각 stack validator(Kotlin/Java/Python/TypeScript)에서 hardcoded 검증 대상 list/regex를 제거하고 `docs/harness/manifest.json`을 single source of truth로 사용한다. 이 위에 추가로 (a) severity 3단계(INFO/WARN/ERROR) 분류와 ERROR-only fail, (b) Kotlin 코드 스타일 강제(if-else→when, MutableList 0건, raw string template 우선), (c) 가능한 곳에서 정규식 대신 native parser/AST 사용을 도입한다.

## Non-Goals

- 검증 *대상*의 의미적 변경(어떤 파일/디렉토리/패턴을 검증할지는 동일하게 유지)
- `.claude/agents/`, `.claude/skills/` 구조 변경
- 새 stack adapter 추가

## Phases

### Phase 1: Manifest schema extension

- [x] Task 1.1 — manifest.json에 새 필드 도입: `requiredDocHeadings`, `requiredContentChecks`, `activeAssetBases`, `excludedActiveAssetSubtrees`, `activeAssetExtensions`, `leakPatterns`, `expectedValidationCommands`, `hookStages`, `completedPlanDirectory`, `unfinishedTaskPattern`, `envShebangBases`. (harness는 versioning하지 않으므로 schemaVersion 같은 필드는 두지 않는다)
- [x] Task 1.2 — backwards-compat 정책 명시

### Phase 2: Gradle validator slimming

- [x] Task 2.1 — `HarnessValidationPlugin.kt`의 companion 상수 제거, manifest 로더 사용 (subagent: general-purpose)
- [x] Task 2.2 — inner class를 8개 이하로 단순화 (subagent: general-purpose)

### Phase 3: Maven/uv/bun validator slimming

- [x] Task 3.1 — Maven `HarnessValidateMojo.java` manifest-driven 슬림화 (subagent: general-purpose)
- [x] Task 3.2 — uv `harness_validate.py` manifest-driven 슬림화 (subagent: general-purpose)
- [x] Task 3.3 — bun `harness-validate.ts` manifest-driven 슬림화 (subagent: general-purpose)

### Phase 4: Self-check + dry-run validation

- [x] Task 4.1 — `plugin-self-check.sh` PASS
- [x] Task 4.2 — `install-harness.sh --mode bun` dry-run 후 bun validator PASS
- [x] Task 4.3 — 모든 validator에서 hardcoded list 0건 확인

### Phase 5: Kotlin immutable signatures (MutableList 0)

- [x] Task 5.1 — `HarnessValidationPlugin.kt`의 모든 sub-validator 시그니처에서 `MutableList<String>` 파라미터 제거. sub-validator는 `List<String>` 반환, main `validate()`가 `buildList { addAll(...) }`로 합침. helper 4종(SafetyCheck/ScanResult/HookCheck/ManifestLoad) data class 도입 (subagent: general-purpose)

### Phase 6: active asset exclude 정확 매치 + evolution-log 제거

- [x] Task 6.1 — manifest.json `excludedActiveAssetSubtrees`에 `docs/harness/manifest.json` 추가 (self-leak 방지)
- [x] Task 6.2 — bun/Python validator의 prefix-only 비교를 `path == subtree || prefix` 로 보강. Java/Kotlin은 `Path.startsWith` 기반이라 동등 매치 자동 지원 — 추가 작업 불필요 확인
- [x] Task 6.3 — `templates/common/docs/harness/evolution-log.md` 제거 + manifest/AGENTS.md/CLAUDE.md/harness-evolve SKILL의 cross-ref를 `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` 기반으로 변경

### Phase 7: Severity 3단계 (INFO/WARN/ERROR) 분류

manifest의 각 검증 카테고리에 `severity`를 명시한다. validator는 모든 fail을 출력하되 `ERROR` severity가 0건이면 exit 0, 1건 이상이면 exit 1. `INFO`/`WARN`은 로그에 prefix(`[INFO]`/`[WARN]`)와 함께 출력하되 실패 처리 안 함. 출력 색은 stack tool마다 다르므로 prefix만 강제한다.

- [ ] Task 7.1 — manifest.json에 `severities` 매핑 추가: 각 검증 카테고리(`requiredFiles`, `requiredDirectories`, `emptyDirectoryKeepFiles`, `requiredDocHeadings`, `requiredContentChecks`, `leakPatterns`, `activeAssetBases`, `hookStage`, `validationCommand`, `envShebang`, `completedPlanDirectory`) → `ERROR|WARN|INFO`. 기본은 `ERROR`. 본 commit에서 기본값(`requiredFiles`/`requiredDirectories`/`requiredContentChecks`/`leakPatterns`/`validationCommand`/`completedPlanDirectory` = `ERROR`, 나머지 = `WARN`)으로 시작.
- [ ] Task 7.2 — Kotlin/Java/Python/TS validator의 `validate()`가 `(severity, message)` 페어 또는 `data class Finding(val severity, val message)` 형태로 결과를 모은다. 출력은 `printf "[%s] %s\n" severity message`. ERROR 0건이면 exit 0. (subagent: general-purpose × 4)
- [ ] Task 7.3 — dry-run install 후 출력 형식이 prefixed 되는지 확인. self-check.sh의 require_text에 ERROR/WARN/INFO 패턴이 필요한 경우 갱신.

### Phase 8: Kotlin 스타일 강제

- [ ] Task 8.1 — `HarnessValidationPlugin.kt`에서 `if (...) { ... } else { ... }` 형태로 `else` 절로 끝나는 if문을 모두 `when` 표현식으로 교체 (subagent: general-purpose)
- [ ] Task 8.2 — string 연결(`"text " + variable + " more"`)을 Kotlin `"text $variable more"` 또는 `"""..."""` template으로 교체. 정규식 raw string은 그대로 유지 (subagent: general-purpose)
- [ ] Task 8.3 — `MutableList`/`mutableListOf` 0건 유지 확인 + `MutableSet`/`MutableMap`도 0건

### Phase 9: TypeScript/JavaScript template literal 정규화

- [ ] Task 9.1 — `harness-validate.ts`에서 string 연결(`"text " + variable`)을 template literal로 교체. backtick template이 이미 일관되게 사용되는지 확인. (subagent: general-purpose)

### Phase 10: AST/native parser 사용 강화

지금까지 manifest JSON과 markdown frontmatter 검사 일부가 정규식 기반이다. native parser/AST로 교체해 fragile한 정규식을 제거한다.

- [ ] Task 10.1 — Kotlin validator: manifest JSON 파싱을 `groovy.json.JsonSlurper`(Gradle 환경 always available) 또는 build.gradle.kts에 `kotlinx-serialization-json` 추가 후 사용. 정규식 기반 `parseStringArray`/`parseContentChecks`/`parseLeakPatterns`/`parseHookStages` 모두 제거 (subagent: general-purpose)
- [ ] Task 10.2 — Java Maven validator: pom.xml에 jackson-databind 의존성 추가. 정규식 기반 `extractStringList` 등 제거, Jackson `ObjectMapper`로 manifest 파싱. (subagent: general-purpose)
- [ ] Task 10.3 — markdown frontmatter 검사(`(?m)^name:\s*[-a-z0-9]+\s*$`, `(?m)^description:\s*.+$`)도 각 stack에서 YAML parser로 교체 가능하면 교체. dependency 부담이 크면 정규식 유지. (subagent: general-purpose)

### Phase 11.5: Hardcoded severity → manifest 기반 severity 치환

- [x] Task 11.5.1 — `bun harness-validate.ts`의 `DEFAULT_SEVERITY` 매핑 제거. severity 미지정 시 무조건 `"ERROR"` fallback
- [ ] Task 11.5.2 — Kotlin `HarnessValidationPlugin.kt`의 `Severity.ERROR` 36건, Java `HarnessValidateMojo.java`의 3건, TS 1건, Python의 잔존 `Finding("ERROR", ...)`을 모두 `severityOf(manifest, category)`/`getSeverity(category)`/`severity_for(manifest, category)`/`parseSeverity(manifest, category)` 호출로 치환. manifest 로딩 실패 fallback 1건만 예외로 hardcoded ERROR 허용 (subagent: general-purpose)

### Phase 12: Manifest add-on architecture

manifest의 각 옵션은 `HarnessValidationPlugin`(코어) 위에서 동작하는 *check add-on*이다. 옵션이 manifest에 등록되어 있을 때만 add-on이 활성화되고, 옵션이 없으면 통째로 skip.

- [ ] Task 12.1 — `HarnessCheck` 인터페이스/protocol 도입(stack 4종):
  ```kotlin
  interface HarnessCheck {
      val category: String
      fun applies(manifest: String): Boolean
      fun validate(root: File, manifest: String): List<Finding>
  }
  ```
  Java는 `interface HarnessCheck { String category(); boolean applies(...); List<Finding> validate(...); }`, Python은 `Protocol` 또는 dataclass, TS는 `interface HarnessCheck`. (subagent: general-purpose × 4)
- [ ] Task 12.2 — 각 검증을 별도 add-on 클래스/함수로 분리: `RequiredFilesCheck`, `RequiredDirectoriesCheck`, `KeepfileCheck`, `RequiredTemplateGroupsCheck`, `RequiredDocHeadingsCheck`, `RequiredContentCheck`, `ScaffoldLeakCheck`, `AgentFrontmatterCheck`, `SkillFrontmatterCheck`, `HookStageCheck`, `HookCommandCheck`, `CiCommandMatchCheck`, `EnvShebangCheck`, `ForbidUncheckedTasksCheck`. registry는 `listOf(...)` (subagent: general-purpose × 4)
- [ ] Task 12.3 — main `validate()`는 registry를 enumerate해 `if (check.applies(manifest)) addAll(check.validate(root, manifest))` 형태로 호출 (subagent: general-purpose × 4)
- [ ] Task 12.4 — bun TS의 helper 함수(`isSafeFile`, `walk` 등)가 module-level `manifest` closure에 의존하던 부분을 명시적 인자 전달로 정리 (subagent: general-purpose)

### Phase 13: Self-documenting manifest schema + add-on vs metadata 분리

직전 schema는 옵션마다 `severity` + items/value만 두고 동작은 validator 코드에 숨겨져 있었다. 사용자 지적: (a) `severity`가 붙은 옵션 중 일부는 add-on 단위가 아니다(예: `templateGroups`는 데이터 정의일 뿐, `requireTemplateGroups`라는 check가 따로 있어야 한다). (b) 대상 경로가 옵션 안에 명시되어야 한다. (c) manifest 자체가 self-documenting 문서여야 한다.

이를 반영해 manifest를 두 종류 entry로 재구성한다:

- **Check add-on**: `description`(무엇을 검증하는지), `severity`(ERROR|WARN|INFO; 미지정 시 ERROR), 그리고 검증 대상 경로/데이터를 명시한 sub-fields. 1 entry = 1 HarnessCheck add-on.
- **Metadata / data**: `description`만 두고 severity 없음. validator는 이를 *읽지만 검증하지는 않는다* (예: `seedFiles`, `generatedArtifacts`, `harnessEvolution`, `teamPatterns`).

#### Phase 13.1 — 새 manifest schema 작성 (self-documenting + 동작 명시 + add-on/metadata 분리). 또한 각 add-on entry는 `failureMessageTemplate`을 명시해 사용자에게 보여줄 메시지를 manifest에서 결정하게 한다.

Check add-on entries (각 entry는 description + severity + 대상 경로/데이터):

- `requireFilesExist { description, severity, paths }` (← 기존 `requiredFiles`)
- `requireDirectoriesExist { description, severity, paths }` (← `requiredDirectories`)
- `requireKeepfileInEmptyDirectories { description, severity, directories }` (← `emptyDirectoryKeepFiles`)
- `requireTemplateGroups { description, severity, targetRoot: "docs/harness/templates", groups }` (← `templateGroups`)
- `requireDocHeadings { description, severity, sourceFilesFromCategory: "requireFilesExist", filter: { prefix, suffix }, headings }` (← `requiredDocHeadings`. 대상은 requireFilesExist 결과 중 docs/*.md)
- `requireDocContent { description, severity, checks: [{ files, containsAll, failureMessage }] }` (← `requiredContentChecks`)
- `requireAgentFrontmatter { description, severity, directory: ".claude/agents", filenamePattern: "*.md", requiredFields: ["name", "description"], namePattern: "^[-a-z0-9]+$" }`
- `requireSkillFrontmatter { description, severity, rootDirectory: ".claude/skills", filename: "SKILL.md", requiredFields: ["description"] }`
- `forbidScaffoldLeaks { description, severity, scope: { bases, excludedSubtrees, extensions }, patterns: [{pattern, label}] }` (← `leakPatterns` + `activeAssets`)
- `requireHookShebang { description, severity, hooks, expectedShebang: "#!/usr/bin/env sh" }` (← `hookFirstLine`)
- `requireHookExecutable { description, severity, hooks }`
- `requireHookGeneratedMarker { description, severity, hooks, markerTemplate: "# Harness generated hook: {name}", placeholderForbidden: "packaged placeholder is replaced during harness installation" }`
- `requireHookStage { description, severity, stages: { gradle: {pre-commit, pre-push}, maven: ..., uv: ..., bun: ... }, markerTemplate: "# Harness stage: {stage}" }`
- `requireHookCommand { description, severity, prePushHook, preCommitHook, allowedCommands: { gradle, maven, uv, bun }, allowedPreCommitCommands: { gradle } }`
- `requireCiCommandMatchesHook { description, severity, ciFiles: [...], referenceHook: "docs/harness/git-hooks/pre-push" }`
- `requireEnvShebangUnder { description, severity, directories, expectedPrefix: "#!/usr/bin/env " }`
- `forbidUncheckedTasksUnder { description, severity, directory: "docs/exec-plans/completed", filenamePattern: "*.md", uncheckedTaskPattern: "^\\s*-\\s*\\[ \\]\\s" }`
- `forbidUnsafeSymlinks { description, severity, allowedSymlinkPairs: [["AGENTS.md", "CLAUDE.md"]] }`

Metadata entries (severity 없음, validator는 검증 안 함):

- `seedFiles { description, paths }` (← `optionalSeedFiles`)
- `generatedArtifacts { description, path, placeholder, policy, metadata }`
- `harnessEvolution { description, policy }`
- `teamPatterns { description, patterns }`

각 entry의 `description`은 그 add-on이 *무엇을, 어디에서, 어떻게* 검증/표현하는지 한 문장으로 명시. manifest를 읽는 사람이 코드를 보지 않고도 동작을 이해할 수 있어야 한다.

#### Phase 13.2 — 4 validator를 새 schema에 맞춰 마이그레이션 (subagent: general-purpose × 4)

각 stack validator는 새 카테고리 이름을 사용하고, 데이터-only entry는 검증 대상에서 제외한다. add-on registry는 Phase 12에서 도입한 인터페이스를 사용한다.

#### Phase 13.3 — 코드 내 함수/식별자 이름 정리 (subagent: general-purpose × 4)

- bun: `walk` → `walkDirectory`, `safeFileOrWalk` → `collectFilesUnder`, `manifestArray`/`manifestObject` → `readStringArray`/`readJsonObject`, `validateContentChecks` → `validateRequiredContent`
- Python: `safe_walk` → `walk_directory`, `safe_file_or_walk` → `collect_files_under`, `manifest_list` → `read_string_array`
- Java: `safeFileOrWalk` → `collectFilesUnder`, `extractStringList`/`extractWrappedStringList` → `readStringArray`
- Kotlin: `safeFileOrWalk` → `collectFilesUnder`, `parseStringArray` → `readStringArray`, `parseContentChecks` → `readContentChecks`, `parseLeakPatterns` → `readLeakPatterns`

### Phase 13.5: 모든 stack에서 early/mid return 제거 (조건 reverse)

사용자 지침: 함수 중간에서 `return`/`exit`로 빠져나가지 말고 조건을 reverse해서 single-exit 또는 when/if-else 구조로 표현. validator code가 early return으로 가득하므로 일괄 정리.

- [ ] Task 13.5.1 — Kotlin `HarnessValidationPlugin.kt`: `if (...) return` / `if (...) return@forEach` / `if (...) return null` 패턴을 모두 reverse 조건 + when 표현식으로 교체. sub-validator 함수는 `buildList { if (...) { ... } }` 형태로 single-exit (subagent: general-purpose)
- [ ] Task 13.5.2 — Java `HarnessValidateMojo.java`: early return 모두 reverse 조건. inner validator 함수가 mid-return 없이 single return List<Finding>으로 구성 (subagent: general-purpose)
- [ ] Task 13.5.3 — Python `harness_validate.py`: `return ()` early return을 모두 reverse `if`로 교체하고 함수 끝에서 단일 return tuple (subagent: general-purpose)
- [ ] Task 13.5.4 — TypeScript `harness-validate.ts`: early `return` 모두 reverse 조건 + single return 또는 nested if-else (subagent: general-purpose)
- [ ] Task 13.5.5 — install-harness.sh의 helper 함수도 `return 0` early return 패턴을 검토. 단 shell script는 early return이 관용적이라 함수당 1건 정도는 허용. orchestrator가 직접 검토 (subagent: harness:harness-architect)

### Phase 13.6: Silent failure 제거 (catch 블록 처리 + emptyList 반환 정리)

사용자 지침: (a) `try { ... } catch (e) { return "" }` / `catch { return null }` 같이 예외를 무시하고 빈 값을 반환하는 패턴 금지. catch 블록은 발생한 예외를 *명시적으로* finding으로 변환해 호출자가 알 수 있게 한다. (b) `emptyList()` / `tuple()` / `[]` 같은 "결과 없음" 반환을 silent로 사용하지 않는다. 결과 없음은 *명시적 finding* 또는 *Result type*으로 표현한다.

- [ ] Task 13.6.1 — 4 stack validator의 모든 `try { ... } catch (...)` 블록을 점검. catch에서 빈 값을 반환하지 말고 (a) finding을 추가해 호출자에 전달 (b) 가능한 경우 예외를 다시 throw해 main에서 ManifestLoad failure로 단일 처리. 예외 종류별 finding category(`manifestParity` / `symlinkSafety` / `ioFailure` 등) 매핑 명시 (subagent: general-purpose × 4)
- [ ] Task 13.6.2 — `emptyList()` / `listOf()` 빈 반환을 silent 결과 표시로 쓰지 않도록 정리:
  - `safeFileOrWalk(unsafe symlink)` 같은 함수는 빈 list 대신 `ScanResult(emptyList, finding)` 같은 명시적 결과 (finding 포함) 반환
  - `extractObjectBody`가 manifest에서 못 찾으면 `null` 대신 `ParseResult.NotFound(category)` 또는 `Finding(manifestParity, ...)`로 표현
  - manifest 옵션이 비어 있는 정상 경우(예: `seedFiles.paths = []`)는 그대로 empty 허용 (이는 정상)
- [ ] Task 13.6.3 — Python의 `return ()` / Kotlin `emptyList()` / TS `[]` / Java `List.of()` 사용처를 모두 grep해서 silent failure 가능성 검토 (subagent: general-purpose × 4)

### Phase 14: Gradle buildSrc 재배치 + assets/ 디렉토리 컨벤션

- [ ] Task 14.1 — skill 디렉토리 컨벤션 정리: `skills/harness-install/templates/` → `skills/harness-install/assets/` (sinon plugin authoring 컨벤션 — skills는 templates 아닌 assets). install-harness.sh가 새 위치를 가리키도록 갱신
- [ ] Task 14.2 — Gradle adapter를 `gradle-plugin/` composite build에서 `buildSrc/` 형태로 재배치 가능한지 검토. buildSrc는 Gradle root project가 자동 인식하므로 `includeBuild` 명시 불필요. 대신 target에 `buildSrc/`를 두는 것이 *target build에 영향*을 미치므로 적절한지 검증 후 결정 (subagent: harness:harness-architect)
- [ ] Task 14.3 — Maven/uv/bun adapter도 비슷한 stack-네이티브 위치로 재배치할 만한 게 있는지 검토

### Phase 15: jsonc 지원 검토 (선택)

- [ ] Task 15.1 — manifest.json을 manifest.jsonc로 옮길 시 cost/benefit. 4 stack의 jsonc parser 의존성 추가 검토 (subagent: harness:harness-architect)

### Phase 16: Self-check + dry-run 재검증 + plan completion

- [ ] Task 16.1 — `plugin-self-check.sh` PASS
- [ ] Task 16.2 — `install-harness.sh --mode bun` dry-run → bun validator `Harness validation passed`, ERROR severity 0건
- [ ] Task 16.3 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status/Completed 갱신

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator. severity 도입 후에는 `[ERROR]`/`[WARN]`/`[INFO]` prefix가 stderr에 출력되는지 확인.

## Rollback Criteria

- manifest 스키마 변경이 target validator를 break시키면 그 commit을 그대로 `git revert`로 되돌린다. harness는 versioning하지 않으므로 "schema bump을 되돌린다" 같은 개념이 없고 — 단지 *현재 committed 상태가 진실*이다.
- AST/native parser 도입으로 인해 stack 의존성이 늘어 install 시간이 현저히 증가하면 정규식 기반으로 부분 롤백 가능 (Phase 10 task별 분리).

## Completion

모든 task가 체크되면 본 plan을 `docs/exec-plans/completed/`로 이동하고 `Status: completed`, `Completed: yyyy-MM-dd`를 기록한다.
