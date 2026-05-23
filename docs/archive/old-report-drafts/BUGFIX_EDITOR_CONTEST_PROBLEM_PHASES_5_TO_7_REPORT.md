# Bugfix Report: Editor, Contest Editing, Old Contest Problems

## Changed files

Phase 5-7 changes:
- `UI/src/hooks/useCodeDraft.ts`
- `UI/src/admin/components/EditContestModal.tsx`
- `UI/src/admin/components/ProblemsView.tsx`
- `UI/src/admin/components/RejudgeView.tsx`
- `backend/src/main/java/com/server/contestControl/contestServer/service/ContestService.java`
- `backend/src/main/java/com/server/contestControl/contestServer/service/ProblemService.java`
- `backend/src/main/java/com/server/contestControl/contestServer/service/TestCaseService.java`
- `backend/src/test/java/com/server/contestControl/contestServer/service/ContestServiceTest.java`

The worktree also still contains previous phase 1-4 scoreboard/reveal changes; those are not part of this report.

## What was fixed

### Phase 5: Code editor draft persistence

Before:
- Returning from the scoreboard could remount the editor with an empty initial state before draft restoration completed.
- Draft storage used the older `draft_{userId}_{contestId}_{problemId}_{language}` key format only.

After:
- Drafts now initialize synchronously from local storage on editor mount.
- Draft keys are scoped by user, contest, problem, and language with `auraC2:draft:{userId}:{contestId}:{problemId}:{language}`.
- Existing legacy draft keys are still read as a fallback, so old local drafts are not discarded.
- Flush, visibility-change, beforeunload, tab-switch, problem-switch, language-switch, and unmount saves continue to write the latest editor text.
- Submitting code does not clear the draft.

### Phase 6: Contest editing rules

Before:
- Backend rejected contest detail edits unless persisted status was `UPCOMING`.
- Frontend disabled all edit fields and the save button for non-upcoming contests.

After:
- `UPCOMING`: title, description, start time, duration, scoreboard freeze, and penalty are editable.
- `RUNNING` / `PAUSED`: title, description, scoreboard freeze, and penalty are editable; start time and duration are locked with clear 400 errors if changed.
- `ENDED`: title and description only; timing, freeze, and penalty changes are rejected because they alter historical scoreboard meaning.
- Successful edits still publish `ContestUpdatedEvent.Reason.UPDATED`, preserving SSE update behavior and scheduler rescheduling for upcoming timing edits.
- The admin edit modal disables locked fields and explains why.

### Phase 7: Old contest problem/testcase editing

Before:
- Admin Problems page was tied to the current selected contest, so ended contests were not practical to manage from that page.
- There was no ended-contest warning or rejudge handoff near old problem/testcase edits.

After:
- Problems page has a contest selector covering active, upcoming, paused, and ended contests.
- Admin can view and manage problems/test cases for ended contests through the existing admin-only endpoints.
- Ended contests show a warning: editing problems/test cases may affect rejudge results.
- Warning includes an `Open Rejudge` action that navigates to rejudge with the selected contest preselected.
- Problem and testcase create/update paths are explicitly transactional.
- No automatic rejudge is triggered.

## Backend changes

- Reworked `ContestService.updateContestDetails` to apply status-aware edit policy using effective contest state.
- Added validation helpers for safe fields, locked timing fields, freeze duration bounds, and ended-contest scoring locks.
- Kept existing contest update event publishing.
- Added `@Transactional` to problem create/update and testcase add/update service methods.
- Updated `ContestServiceTest` for running, paused, ended, and unsafe edit behavior.

## Frontend changes

- `useCodeDraft` now synchronously restores drafts and uses the new stable key format with legacy fallback.
- `EditContestModal` now enables/disables fields according to contest state and submits exact original locked values.
- `ProblemsView` now loads all contest options, persists selected contest in the URL query, supports ended contests, and shows the integrity/rejudge warning.
- `RejudgeView` now honors `?contestId=` so the Problems page handoff opens the intended contest.

## Tests added/updated

- Updated backend contest service tests:
  - Upcoming contests still edit all fields.
  - Running contests allow safe metadata/scoring edits.
  - Running contests reject start-time changes.
  - Paused contests allow safe metadata/scoring edits.
  - Ended contests allow title/description only.
  - Ended contests reject scoring changes.
- No new frontend test framework was added.

## Commands run

- `cd backend`
- `.\mvnw.cmd test`
- `cd UI`
- `npm run build`
- `npx tsc --noEmit`
- `git diff --check`

## Build/test results

- Backend `.\mvnw.cmd test`: PASSED. 78 tests passed.
- Frontend `npm run build`: PASSED. Vite emitted the existing large chunk warning.
- `git diff --check`: PASSED, with Windows CRLF warnings only.
- Frontend `npx tsc --noEmit`: FAILED.
  - Test files reference Vitest globals (`vi`, `describe`, `it`, `expect`) while `tsconfig.json` only includes `vite/client` types.
  - Existing `src/team/components/ui/resizable.tsx` has a `ref` type error against `react-resizable-panels`.
  - These failures are not caused by the phase 5-7 changes; the Vite production build passed.

Backend test logs also showed existing non-fatal RabbitMQ connection-refused messages during Spring context startup.

## Remaining limitations

- Frontend standalone `tsc --noEmit` needs project-level test type configuration and the existing resizable ref typing issue resolved.
- The Problems page warns about old contest edits and links to rejudge, but does not automatically rejudge by design.

## Manual verification steps

1. Team editor draft:
   - Open a running team workspace.
   - Select a problem and language.
   - Type code.
   - Click `Scoreboard`.
   - Return to `Workspace`.
   - Confirm the code remains.
   - Change problem and language, then return to the original combination and confirm each draft is separate.
   - Refresh the page and confirm the saved draft restores.

2. Contest edit rules:
   - Edit an upcoming contest and confirm timing/scoring fields are enabled.
   - Start or pause a contest and confirm title, description, freeze, and penalty are enabled while start time and duration are disabled.
   - Attempt to change locked timing fields through the API and confirm a clear 400 error.
   - End a contest and confirm only title and description are editable.

3. Old contest problem/testcase editing:
   - Go to Admin `Problems`.
   - Select an ended contest from the contest dropdown.
   - Confirm the ended-contest warning appears.
   - Edit a problem and add/edit/delete a testcase.
   - Click `Open Rejudge` and confirm Rejudge opens with that contest selected.
