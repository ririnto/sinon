---
status: completed
created: 2026-05-23
updated: 2026-05-23
completed: 2026-05-23
author: ririnto
assignee: ririnto
---

# 2026-05-23-template-default-content

## Goal

target repository에 install된 직후 docs/ + ARCHITECTURE.md template들이 "바로 사용 가능한" 수준의 default content를 갖도록 한다. 현재는 각 파일이 골격(`{{placeholder}}`만)을 갖춘 상태인데, 일반적·합리적인 default를 채워서 사용자가 install 직후 바로 읽고 그 위에 점진적으로 자신의 프로젝트 컨텍스트를 덮어쓸 수 있도록 만든다.

원칙:

- 일반적인 SaaS/플랫폼 프로젝트에 두루 적용 가능한 *neutral default*. 특정 stack(예: TypeScript 전용)에 종속되지 않게 작성.
- *example* 데이터 라벨을 명시하여 사용자가 교체할 자리임을 분명히 한다 (e.g., `Example: ...`, `(replace with your own)`).
- 진짜로 사용자별이어야만 채워질 수 있는 *식별자/이름/URL*은 `{{...}}` placeholder를 유지. 그 외의 내용은 default text로 채운다.
- BCP 14 normative 키워드를 default content에서 함부로 사용하지 않는다(MUST/SHOULD는 *contract*에 한정).
- mermaid 다이어그램은 title frontmatter + 흑색 배경 / 라임 그린 강조 / stadium 노드 컨벤션을 따른다(이미 reference에서 검증됨).

## Plan Convention

- Phase는 순차 실행, Task는 phase 내 병렬 안전.
- 각 task가 다른 파일을 다루므로 Phase 1의 모든 task는 병렬 위임.

## Phases

### [x] Phase 1: docs/ + ARCHITECTURE template default content (병렬)

- [x] Task 1.1 — ARCHITECTURE.md: Domain Map (Identity/Catalog/Notifications) + Package Layering mermaid (title + OpenAI-style theme) + Data Flow + External Integrations + Validation Surfaces default
- [x] Task 1.2 — docs/PLANS.md: Roadmap 표 5 milestones (M1~M5: Discovery → GA) + Sequencing Rationale + Dependencies default
- [x] Task 1.3 — docs/DESIGN.md: mermaid title + theme + Cross-cutting 5 categories + 6 Taste Invariants + 5 Review Criteria default
- [x] Task 1.4 — docs/PRODUCT_SENSE.md: 5 Product Principles + User Model (daily power user + 3 secondary) + 5 Design Tone + 5 Refusal Signals default
- [x] Task 1.5 — docs/QUALITY_SCORE.md: A-D Grading Scale + Domain Scores 3 rows + Layer Scores 3 rows + Gap Tracking example
- [x] Task 1.6 — docs/RELIABILITY.md: SLO 4 rows + 5 Failure Modes + 4 Recovery Procedures + 4 Observability bullets default
- [x] Task 1.7 — docs/SECURITY.md: STRIDE 6 categories + Secret Management 4 + Permission Boundaries 4 + Audit Logging 4 default
- [x] Task 1.8 — docs/FRONTEND.md: Exposed Surfaces 표 6 rows (Web/Mobile/API/CLI/Webhook/SDK) + Surface Legibility/Design System/Boundaries/Accessibility default
- [x] Task 1.9 — docs/design-docs/core-beliefs.md: 5 beliefs rationale + 3-4 consequences each default
- [x] Task 1.10 — docs/exec-plans/tech-debt-tracker.md: Entries 표 3 default rows (TD-1/2/3) + Conventions 강화
- [x] Task 1.11 — docs/references/README.md: Shipped References (OpenAI + Symphony) + Adding a New Reference + Template + Validation

### [x] Phase 2: 검증

- [x] Task 2.1 — plugin-self-check.sh PASS (exit=0)
- [x] Task 2.2 — shellcheck on install-harness.sh + plugin-self-check.sh + detect-stack.sh PASS (exit=0)
- [x] Task 2.3 — install --mode bun dry-run 성공 + docs/*.md 모두 정상 H1 헤딩 + ARCHITECTURE.md root 위치 확인

### [x] Phase 3: Plan completion

- [x] Task 3.1 — 본 plan을 `docs/exec-plans/completed/`로 이동, Status `completed` + `Completed: 2026-05-23` 기록

## Validation

phase 종료 시 `plugin-self-check.sh` + `shellcheck`.

## Rollback Criteria

default content가 install 후 leak 검사에 걸리는 경우 `git revert` 후 단계적 재시도.

## Completion

모든 task 체크 후 `docs/exec-plans/completed/`로 이동.
