## Code Review: Security Audit — Hardcoded Private Paths (Cross-Cutting)
**Date**: 2026-02-28
**Reviewer**: Staff Engineer Code Reviewer AI
**Scope**: Cross-cutting security audit triggered by discovery of a hardcoded
  private machine path of the form `C:\Users\<username>\...\platform-tools\adb.exe`
  in tracked files. Active feature context: `005-fix-task-end-time`.
**Verdict**: APPROVED (all violations remediated in this session)
**Build**: N/A — this is a documentation/configuration-only change; no source
  code was modified. No build/test run required.

---

### 1. Diff Summary

| # | File | Lines | Layer | Risk |
|---|------|-------|-------|------|
| 1 | `.gitignore` | +4 | Config | LOW |
| 2 | `.specify/memory/constitution.md` | +38 −8 | Governance | MEDIUM |
| 3 | `.specify/memory/failure-log.md` | +12 | Memory | LOW |
| 4 | `.specify/review/code-review.md` | +5 −2 | Review tooling | LOW |
| 5 | `.specify/templates/spec-template.md` | +2 −2 | Templates | LOW |
| 6 | `specs/004-task-ui-scheduling/tasks.md` | +2 −1 | Spec docs | LOW |
| 7 | `specs/005-fix-task-end-time/quickstart.md` | +2 −1 | Spec docs | LOW |
| NEW | `.local/README.md` | +35 | Config | LOW |
| NEW | `.local/env.ps1.template` | +22 | Config | LOW |

All changes are documentation, configuration, and governance files.
No production Kotlin, Room DAO, ViewModel, or Compose code was touched.

---

### 2. Constitution Compliance

| Principle | Check | Verdict |
|-----------|-------|---------|
| I. Additive Logic | No existing user flows affected — doc/config only | PASS |
| II. Data Integrity | No data model changes | PASS |
| III. Layered Architecture | No layer boundary changes | PASS |
| IV. State-Driven UI | No UI changes | PASS |
| V. Explicit Dependencies | No dependency changes | PASS |
| VI. Security & Privacy | **Root of this review.** All 4 private-path instances removed. Principle VI strengthened. Gate 4 strengthened. `.local/` created and gitignored. | PASS |
| VII. Emoji Non-Negotiable | Not applicable | N/A |
| VIII. Device Gate | ADB examples updated to use `$env:ANDROID_HOME` | PASS |
| IX. Minimal Precise | Changes are minimal — only what was needed to fix violation and prevent recurrence | PASS |

---

### 3. Violation Analysis

#### FAIL-007 — Root Cause

When Principle VIII (Device Connectivity Gate) was authored at constitution v1.5.0,
a concrete working `$adb` assignment was copied verbatim from a live terminal session.
That session used the `sdk.dir` value from `local.properties` — a file that is
correctly gitignored but whose content leaked into the constitution's code example.

**Conceptual gap that allowed propagation**: Gate 4 (Security) was understood to
apply to production source code (`.kt`, `.xml`, `.kts`). No rule explicitly stated
that fenced code blocks in `.md` files were subject to the same check. As a result:

1. The initial violation entered `constitution.md` (v1.5.0, 2026-02-26).
2. The constitution example was used as the canonical snippet for Principle VIII.
3. It propagated verbatim into `specs/004-task-ui-scheduling/tasks.md` (~2026-02-27).
4. It propagated again into `specs/005-fix-task-end-time/quickstart.md` (~2026-02-28).

The violation was **not** present in any `.kt`, `.xml`, `.kts`, `.properties`, or
`.ps1` script file — only in markdown documentation.

---

### 4. Security Scan Results

#### Tracked files scan (git grep — all `.md`, `.kt`, `.kts`, `.xml`, `.ps1`, `.properties`)

```
Pattern: C:\Users\[name]  → 0 remaining instances in functional code examples
Pattern: AppData\Local    → 0 remaining instances in functional code examples
Pattern: /Users/[name]    → 0 matches
Pattern: /home/[name]     → 0 matches
Pattern: email addresses  → 0 real addresses found (only template placeholders)
Pattern: API keys/tokens  → 0 found (documentation references only)
Pattern: passwords        → 0 real passwords (template placeholders only)
```

Remaining documentary references (appropriate — historical record):
- `constitution.md` version change comment: names what was removed ✓
- `failure-log.md` FAIL-007 symptom: describes the violation being logged ✓

#### Generated/ignored files scan

Private paths found in `app/build/` (HTML test reports, manifest merger logs).
These files are gitignored by `/**/build/` — not tracked, not a violation.

#### Other vulnerability checks

| Category | Finding | Status |
|---|---|---|
| SQL injection | Room `@Query` with named parameters throughout | PASS |
| Cleartext HTTP | No network layer; local-only app | N/A |
| Sensitive Logcat | No user data logged in production paths (verified in prior reviews) | PASS |
| Exported components | `android:exported` set explicitly in manifest (prior review) | PASS |
| CVE dependencies | No new third-party dependencies in this change | N/A |

---

### 5. Remediation Applied

| Action | File(s) |
|---|---|
| Removed 4 hardcoded private paths; replaced with `$env:ANDROID_HOME` portable expression | `constitution.md` ×2, `004/tasks.md`, `005/quickstart.md` |
| Strengthened Principle VI with explicit markdown code-block clause | `constitution.md` |
| Added `.local/` folder convention to Principle VI | `constitution.md` |
| Strengthened Gate 4 Security check with explicit grep command for markdown paths | `constitution.md` |
| Added FAIL-007 entry with root cause and prevention rule | `failure-log.md` |
| Updated Step 11 (Repo Hygiene) to require markdown code-block scan | `code-review.md` |
| Updated SE-001/SE-002 in spec template to cover code examples | `spec-template.md` |
| Added `.local/env.ps1` to `.gitignore` | `.gitignore` |
| Created `.local/README.md` and `.local/env.ps1.template` | `.local/` |
| Bumped constitution to v1.6.1 | `constitution.md` |

---

### 6. Prevention — Future Guardrails

1. **Constitution Principle VI** (v1.6.1) now explicitly states that code examples
   in markdown files follow the same no-PII rules as production source code.
2. **Gate 4** now requires: grep all `.md` file changes for `C:\Users\`, `/Users/`,
   `AppData\`, and absolute paths containing usernames before approving.
3. **Step 11 Repo Hygiene** in `code-review.md` now includes a copy-paste grep
   command to run during every review.
4. **SE-002** in `spec-template.md` now calls out fenced code blocks explicitly.
5. **`.local/env.ps1.template`** gives all future contributors a gitignored,
   standard location for machine-specific variables — eliminating the incentive
   to inline them in tracked files.

---

### Issues List

| # | Severity | Location | Description | Resolution |
|---|---|---|---|---|
| I-001 | BLOCK (VI) | `constitution.md:266,513`, `004/tasks.md:42`, `005/quickstart.md:25` | Private machine path `C:\Users\<username>\...` in 4 tracked files | FIXED — replaced with `$env:ANDROID_HOME` expression |
| I-002 | WARN | Gate 4 Security check | Did not cover markdown code blocks — conceptual gap enabled violation to persist and propagate | FIXED — Gate 4 updated in constitution v1.6.1 |
| I-003 | WARN | No `.local/` gitignored convention existed | No designated place for local machine config led to inlining in tracked files | FIXED — `.local/` created and gitignored |

---

**Verdict Rationale**: All blocking issues (I-001) and contributing warning issues
(I-002, I-003) are fully remediated in this session. No production code was changed —
all modifications are to governance, configuration, and documentation. The codebase
is now cleaner than before this audit. No follow-up tasks required.
