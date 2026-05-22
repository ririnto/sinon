# 2026-05-23-ast-based-style-checks

- Status: active
- Created: 2026-05-23
- Last Updated: 2026-05-23
- Completed:
- Author: ririnto
- Assignee: ririnto

## Goal

stack-별 코드 스타일 검사를 *AST 기반*으로 도입한다. 정확성 우선(빌드 시간 무관). 사용자 지침:

- 새 검사 (enabled: true 즉시 적용):
  - `forbidGreaterThanComparison` — `>` / `>=` 대신 `<` / `<=` 만 허용(작은 값이 항상 왼쪽).
  - `forbidBlankLineInLeafFunction` — 내부에 함수를 포함하지 않는 *최종 함수*는 본문에 공백 라인을 두지 않는다(중첩 함수가 있는 외곽 함수는 적용 제외).
- 새 검사 (manifest는 enabled: false로 등록, 구현 후 enable):
  - `forbidEarlyReturn` — early/mid return 금지, single-exit 강제.
  - `forbidSilentCatch` — catch에서 예외 무시 금지.
  - `forbidMutableCollection` — mutableListOf/ArrayList/`[].append`/`arr.push` 누적 금지.
  - `forbidUnstructuredLogging` — println/System.out/print/console.log 금지.
  - `forbidWildcardImport` — wildcard import 금지.
  - `requireImportOverFqn` — FQN 직접 사용 대신 import.
  - `requireDocCommentOnPublicDeclaration` — 외부 노출 declaration에 doc comment.
  - `forbidEmptyCatchBlock` — 빈 catch 블록 금지.
- 기존 정규식 기반 검사도 AST로 마이그레이션: `forbidImplicitLambdaIt`, `requireSingleTopLevelKotlinDeclaration`.

모든 신규/마이그레이션 검사는 *AST*를 사용한다. 정규식은 사용하지 않는다.

manifest schema 결정:

- `parameters.sourceRootsPerStack` — glob-friendly 경로 list. 멀티모듈 지원을 위해 `**/src/main/kotlin` 같은 glob 권장.
- `parameters.extensionsPerStack` — 확장자 list (suffix 아님). 예: TS는 `[".ts", ".tsx"]`.
- 위 두 키는 모든 stack-specific AST 검사에서 공통 사용. (기존 forbidImplicitLambdaIt / requireSingleTopLevelKotlinDeclaration도 통일 schema로 마이그레이션 대상.)

## Plan Convention

- Phase는 순차. Task는 같은 phase 내 병렬 안전.
- AST 의존성을 추가할 때 BOM + Version Catalog를 우선한다(Kotlin/Java).

## Phases

### [ ] Phase 1: manifest 신규 add-on 정의 (직렬, 단일)

- [ ] Task 1.1 — manifest.json에 `forbidGreaterThanComparison`, `forbidBlankLineInLeafFunction` 두 add-on 추가. parameters.directories는 stack-specific(buildSrc/src/main/kotlin, harness-maven-plugin/src/main/java, docs/harness/uv, docs/harness/bun). 통일 schema 유지

### [ ] Phase 2: Python AST 도입 (병렬 task)

uv stack은 표준 `ast` 모듈로 가능. 가장 가벼운 stack부터 진행.

- [ ] Task 2.1 — `assets/uv/runtime/harness_check.py`에 `ForbidGreaterThanComparison` enum value: `ast.parse(text)` 후 `ast.walk`로 `Compare` 노드 순회하여 `Gt`/`GtE` ops 발견 시 Finding 반환
- [ ] Task 2.2 — `assets/uv/runtime/harness_check.py`에 `ForbidBlankLineInLeafFunction` enum value: `ast.FunctionDef`/`AsyncFunctionDef` 순회. body 내부에 `FunctionDef`가 없으면 leaf 함수. leaf인 경우 source code의 함수 body 줄 범위에서 빈 줄 검출 → Finding

### [ ] Phase 3: TypeScript AST 도입 (병렬 task)

bun stack은 `typescript` npm 패키지 추가하여 AST 사용.

- [ ] Task 3.1 — `assets/bun/runtime/harness-check.ts`에 `forbidGreaterThanComparison` 검사: `typescript.createSourceFile`로 파싱, `ts.forEachChild`로 순회, `BinaryExpression` 노드의 operator가 `>`/`>=`이면 Finding
- [ ] Task 3.2 — `assets/bun/runtime/harness-check.ts`에 `forbidBlankLineInLeafFunction` 검사: `FunctionDeclaration`/`MethodDeclaration`/`ArrowFunction` 노드 순회. inner function이 없으면 leaf. body 범위 내 빈 줄 검사
- [ ] Task 3.3 — `package.json` (필요 시 생성) 또는 bun import map에 `typescript@^5` 추가

### [ ] Phase 4: Java AST 도입 (병렬 task)

maven stack은 `com.github.javaparser:javaparser-core`를 BOM 통해 추가. 또는 표준 JDK `com.sun.tools.javac`(internal API라 BOM 없음).

- [ ] Task 4.1 — `harness-maven-plugin/pom.xml`에 javaparser-core 의존성 추가
- [ ] Task 4.2 — `HarnessCheck.java`에 `FORBID_GREATER_THAN_COMPARISON` enum value: JavaParser로 `BinaryExpr` 순회, operator `GREATER`/`GREATER_EQUALS`면 Finding
- [ ] Task 4.3 — `HarnessCheck.java`에 `FORBID_BLANK_LINE_IN_LEAF_FUNCTION` enum value: `MethodDeclaration` 순회. body 안에 `MethodDeclaration`/`LambdaExpr`이 없으면 leaf. body 시작/끝 줄 범위에서 source 빈 줄 검출

### [ ] Phase 5: Kotlin AST 도입 (병렬 task)

gradle stack은 `org.jetbrains.kotlin:kotlin-compiler-embeddable`을 Version Catalog로 추가. PSI 또는 FIR로 AST 접근.

- [ ] Task 5.1 — `gradle/libs.versions.toml`에 kotlin-compiler-embeddable 등록 + `build.gradle.kts`에서 `implementation(libs.kotlin.compiler.embeddable)`
- [ ] Task 5.2 — `HarnessCheck.kt`에 `FORBID_GREATER_THAN_COMPARISON` enum value: PSI로 `KtBinaryExpression` 순회, operationToken이 `KtTokens.GT`/`GTEQ`면 Finding
- [ ] Task 5.3 — `HarnessCheck.kt`에 `FORBID_BLANK_LINE_IN_LEAF_FUNCTION` enum value: `KtNamedFunction`/`KtLambdaExpression` 순회. body 안에 nested function 없으면 leaf. body 텍스트 범위 내 빈 줄 검출
- [ ] Task 5.4 — 기존 `forbidImplicitLambdaIt` (regex 기반) → AST 기반으로 마이그레이션: `KtLambdaExpression`의 valueParameters가 empty면 `it` 사용 의미, 검사
- [ ] Task 5.5 — 기존 `requireSingleTopLevelKotlinDeclaration` (regex 기반) → AST 기반으로 마이그레이션: `KtFile.declarations` 카운트 + 종류 검사

### [ ] Phase 6: 검증

- [ ] Task 6.1 — plugin-self-check.sh + shellcheck PASS
- [ ] Task 6.2 — 각 stack dry-run install + 자체 validator 실행하여 새 검사가 동작하는지 확인 (의도적으로 위반하는 fixture로 fail 확인)

### [ ] Phase 7: Plan completion

- [ ] Task 7.1 — 본 plan을 `docs/exec-plans/completed/`로 이동

## Validation

각 phase 종료 시 `plugin-self-check.sh` PASS + 해당 stack validator 실행 정상.

## Rollback Criteria

AST 의존성 추가로 stack 빌드 시간/크기가 과도하게 증가하거나 정확성 문제가 발생하면 해당 stack의 AST 도입을 `git revert`.
