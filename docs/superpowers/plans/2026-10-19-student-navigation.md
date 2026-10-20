# Student Navigation Implementation Plan

**Goal:** Apply the user's approved option A: remove the student sidebar, use horizontal desktop navigation and mobile bottom navigation, and simplify the homepage around the current assignment.

**Architecture:** Keep the teacher branch of AppShell and its profile/settings behavior. Add a shared student navigation component and scoped student shell styles. Replace the student homepage presentation while retaining existing API calls and assignment entry checks.

**Tech Stack:** Vue 3, TypeScript, Vue Router, scoped CSS, Playwright.

**Spec:** User approved option A from the interactive student navigation preview on 2026-10-19.

## Constraints

- Order: 首页、作业、错题本、学习报告、3D 实验.
- Teacher pages retain their sidebar and existing styles.
- Personal menu moves to the student header; settings, profile editing and logout remain functional.
- Mobile navigation respects safe areas; AI control and page content must clear it.
- Task question and practice routes highlight their parent navigation entry.
- Use real API data; do not carry prototype fixtures into the product.

## Tasks

- [x] Add a browser regression covering student desktop/mobile navigation, assignment entry, profile menu and teacher sidebar; observe the old student layout fail.
- [x] Add shared student navigation and icons; branch AppShell by role, remove duplicated student link arrays, position the personal menu beneath the student avatar.
- [x] Implement the approved homepage: greeting, primary assignment, summary row, recent mistakes, experiment card; remove the repeated “下一步” list and obsolete CSS.
- [x] Keep the mobile AI button/drawer clear of the bottom navigation, including resize and drag behavior.
- [x] Run browser checks at desktop and mobile widths, frontend tests and production build; inspect screenshots and review the diff.

## Review focus

No tasks / scheduled tasks; child routes and reloads; mobile font scaling / long titles; teacher layout isolation; mobile AI control bounds.

## Execution notes

Work directly in the user's existing clean feature branch, codex/simplify-backend, so the approved change is available in their current checkout. No dependency installation, backend change, automatic commit or push is needed.

Verification: 9 frontend unit tests, 4 fixture-based Playwright regressions, and the production build passed. Screenshots inspected at 1440 px and 390 px; maximum font scale checked across 320–1440 px. An independent read-only review found no actionable defects. Experiments use the full content width (1614 px iframe container at a 1920 px viewport), while other student pages retain the approved reading width. Existing Vite bundle-size advisory remains.

## Approved teacher follow-up

The user subsequently requested the same UI layout for teachers. This supersedes the earlier requirement to retain the teacher sidebar. Both roles now share AppShell, AppNavigation and AppIcon; obsolete sidebar state/styles and per-page navigation props were removed. Teacher entries remain 总览、学生名册、题库管理、学生学情、任务管理, with assignment details highlighting 任务管理. Account controls and AI navigation clearance are shared. Teaching workflows and backend APIs are unchanged.

Follow-up verification: 9 frontend unit tests, 5 fixture-based browser regressions and production build passed. Populated teacher screenshots inspected at 1440 px and 390 px; header and document widths checked at 320/390/760/761/800/1000/1440 px with 18 px font. No horizontal overflow or page errors. Independent review found no actionable defects. Existing bundle-size advisory remains.
