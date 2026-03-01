# Specification Quality Checklist: Fix Task End Time Bug (Revised — Iteration 2)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Revised**: February 28, 2026 — iteration 2 after root-cause analysis
**Feature**: [005-fix-task-end-time/spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Root Cause Traceability

- [x] Root cause is identified at the correct layer (Repository, not UI)
- [x] Why previous fix failed is documented
- [x] Why previous tests passed despite the bug is documented
- [x] New test requirements explicitly mandate end-to-end save path coverage

## Notes

- **Iteration 1 failure reason**: All fixes were in the UI date-picker layer. The Repository layer (`TaskRepositoryImpl.updateTask`) unconditionally called `normaliseToMidnight()` on `dueDate` before writing to Room, discarding the time component regardless of what the UI computed.
- **Iteration 1 test gap**: Tests called utility functions directly (e.g., `resolveEditTaskSheetDueDate`) without ever invoking `repository.updateTask()`. The repository normalisation was never exercised.
- **Iteration 2 fixes required**: (1) Remove `normaliseToMidnight` from the `dueDate` save path in `TaskRepositoryImpl.updateTask` and `GlobalHistoryViewModel.saveEditTask`. (2) Add time pickers to `TaskEditSheet` in the History screen. (3) Show start date/time on Home screen task cards.
- **Iteration 2 test requirement**: At minimum one test per save path (Home + History) must verify the value stored in the DAO/Repository matches the H:M value the UI passed — not just that a utility function computes the right output.
