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

- [x] Task 1.1 — manifest.json schemaVersion 0.4.0. 새 필드: `requiredDocHeadings`, `requiredContentChecks`, `activeAssetBases`, `excludedActiveAssetSubtrees`, `activeAssetExtensions`, `leakPatterns`, `expectedValidationCommands`, `hookStages`, `completedPlanDirectory`, `unfinishedTaskPattern`, `envShebangBases` (subagent: main)
- [x] Task 1.2 — backwards-compat 정책 명시 (subagent: main)

### Phase 2: Gradle validator slimming

- [x] Task 2.1 — `HarnessValidationPlugin.kt`의 companion 상수 제거, manifest 로더 사용 (subagent: general-purpose)
- [x] Task 2.2 — inner class를 8개 이하로 단순화 (subagent: general-purpose)

### Phase 3: Maven/uv/bun validator slimming

- [x] Task 3.1 — Maven `HarnessValidateMojo.java` manifest-driven 슬림화 (subagent: general-purpose)
- [x] Task 3.2 — uv `harness_validate.py` manifest-driven 슬림화 (subagent: general-purpose)
- [x] Task 3.3 — bun `harness-validate.ts` manifest-driven 슬림화 (subagent: general-purpose)

### Phase 4: Self-check + dry-run validation

- [x] Task 4.1 — `plugin-self-check.sh` PASS (subagent: main)
- [x] Task 4.2 — `install-harness.sh --mode bun` dry-run 후 bun validator PASS (subagent: main)
- [x] Task 4.3 — 모든 validator에서 hardcoded list 0건 확인 (subagent: main)

### Phase 5: Kotlin immutable signatures (MutableList 0)

- [x] Task 5.1 — `HarnessValidationPlugin.kt`의 모든 sub-validator 시그니처에서 `MutableList<String>` 파라미터 제거. sub-validator는 `List<String>` 반환, main `validate()`가 `buildList { addAll(...) }`로 합침. helper 4종(SafetyCheck/ScanResult/HookCheck/ManifestLoad) data class 도입 (subagent: general-purpose)

### Phase 6: active asset exclude 정확 매치 + evolution-log 제거

- [x] Task 6.1 — manifest.json `excludedActiveAssetSubtrees`에 `docs/harness/manifest.json` 추가 (self-leak 방지) (subagent: main)
- [x] Task 6.2 — bun/Python validator의 prefix-only 비교를 `path == subtree || prefix` 로 보강. Java/Kotlin은 `Path.startsWith` 기반이라 동등 매치 자동 지원 — 추가 작업 불필요 확인 (subagent: main)
- [x] Task 6.3 — `templates/common/docs/harness/evolution-log.md` 제거 + manifest/AGENTS.md/CLAUDE.md/harness-evolve SKILL의 cross-ref를 `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` 기반으로 변경 (subagent: main)

### Phase 7: Severity 3단계 (INFO/WARN/ERROR) 분류

manifest의 각 검증 카테고리에 `severity`를 명시한다. validator는 모든 fail을 출력하되 `ERROR` severity가 0건이면 exit 0, 1건 이상이면 exit 1. `INFO`/`WARN`은 로그에 prefix(`[INFO]`/`[WARN]`)와 함께 출력하되 실패 처리 안 함. 출력 색은 stack tool마다 다르므로 prefix만 강제한다.

- [ ] Task 7.1 — manifest.json에 `severities` 매핑 추가: 각 검증 카테고리(`requiredFiles`, `requiredDirectories`, `emptyDirectoryKeepFiles`, `requiredDocHeadings`, `requiredContentChecks`, `leakPatterns`, `activeAssetBases`, `hookStage`, `validationCommand`, `envShebang`, `completedPlanDirectory`) → `ERROR|WARN|INFO`. 기본은 `ERROR`. 본 commit에서 기본값(`requiredFiles`/`requiredDirectories`/`requiredContentChecks`/`leakPatterns`/`validationCommand`/`completedPlanDirectory` = `ERROR`, 나머지 = `WARN`)으로 시작. (subagent: main)
- [ ] Task 7.2 — Kotlin/Java/Python/TS validator의 `validate()`가 `(severity, message)` 페어 또는 `data class Finding(val severity, val message)` 형태로 결과를 모은다. 출력은 `printf "[%s] %s\n" severity message`. ERROR 0건이면 exit 0. (subagent: general-purpose × 4)
- [ ] Task 7.3 — dry-run install 후 출력 형식이 prefixed 되는지 확인. self-check.sh의 require_text에 ERROR/WARN/INFO 패턴이 필요한 경우 갱신. (subagent: main)

### Phase 8: Kotlin 스타일 강제

- [ ] Task 8.1 — `HarnessValidationPlugin.kt`에서 `if (...) { ... } else { ... }` 형태로 `else` 절로 끝나는 if문을 모두 `when` 표현식으로 교체 (subagent: general-purpose)
- [ ] Task 8.2 — string 연결(`"text " + variable + " more"`)을 Kotlin `"text $variable more"` 또는 `"""..."""` template으로 교체. 정규식 raw string은 그대로 유지 (subagent: general-purpose)
- [ ] Task 8.3 — `MutableList`/`mutableListOf` 0건 유지 확인 + `MutableSet`/`MutableMap`도 0건 (subagent: main)

### Phase 9: TypeScript/JavaScript template literal 정규화

- [ ] Task 9.1 — `harness-validate.ts`에서 string 연결(`"text " + variable`)을 template literal로 교체. backtick template이 이미 일관되게 사용되는지 확인. (subagent: general-purpose)

### Phase 10: AST/native parser 사용 강화

지금까지 manifest JSON과 markdown frontmatter 검사 일부가 정규식 기반이다. native parser/AST로 교체해 fragile한 정규식을 제거한다.

- [ ] Task 10.1 — Kotlin validator: manifest JSON 파싱을 `groovy.json.JsonSlurper`(Gradle 환경 always available) 또는 build.gradle.kts에 `kotlinx-serialization-json` 추가 후 사용. 정규식 기반 `parseStringArray`/`parseContentChecks`/`parseLeakPatterns`/`parseHookStages` 모두 제거 (subagent: general-purpose)
- [ ] Task 10.2 — Java Maven validator: pom.xml에 jackson-databind 의존성 추가. 정규식 기반 `extractStringList` 등 제거, Jackson `ObjectMapper`로 manifest 파싱. (subagent: general-purpose)
- [ ] Task 10.3 — markdown frontmatter 검사(`(?m)^name:\s*[-a-z0-9]+\s*$`, `(?m)^description:\s*.+$`)도 각 stack에서 YAML parser로 교체 가능하면 교체. dependency 부담이 크면 정규식 유지. (subagent: general-purpose)

### Phase 11: Self-check + dry-run 재검증

- [ ] Task 11.1 — `plugin-self-check.sh` PASS (subagent: main)
- [ ] Task 11.2 — `install-harness.sh --mode bun` dry-run → bun validator PASS, ERROR severity 0건 확인 (subagent: main)
- [ ] Task 11.3 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status/Completed 갱신 (subagent: main)

## Validation

각 phase 종료 시 `plugin-self-check.sh`와 dry-run install + bun validator. severity 도입 후에는 `[ERROR]`/`[WARN]`/`[INFO]` prefix가 stderr에 출력되는지 확인.

## Rollback Criteria

- manifest 스키마 변경이 backward-incompat한 방식으로 적용돼 기존 target이 break되면 schemaVersion bump을 되돌리고 새 키를 optional로 다시 도입.
- AST/native parser 도입으로 인해 stack 의존성이 늘어 install 시간이 현저히 증가하면 정규식 기반으로 부분 롤백 가능 (Phase 10 task별 분리).

## Completion

모든 task가 체크되면 본 plan을 `docs/exec-plans/completed/`로 이동하고 `Status: completed`, `Completed: yyyy-MM-dd`를 기록한다.
