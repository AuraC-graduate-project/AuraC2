# AuraC2 UI

React + TypeScript + Vite frontend for AuraC2.

## Run Locally

```bash
npm install
npm run dev
```

Open the URL printed by Vite. The frontend expects the Spring Boot backend to be reachable through the configured Vite proxy/API base.

## Build

```bash
npm run build
```

## Implemented UI Workflows

### Authentication

- Login screen.
- JWT role routing to admin or team workspace.
- Access-token storage and refresh/logout API integration.

### Admin

- Contest overview with active/upcoming/paused/ended buckets and lifecycle controls.
- Contest creation/editing with timing, freeze, and penalty fields.
- Problem create/edit/delete.
- Structured problem statement editing with wide scroll-contained modals, compact secondary editors, public notes, admin-only internal notes, and contestant-style preview support.
- Test-case create/edit/delete with hidden/public visibility.
- Team account registration and user management.
- Submission review with code inspection and live submission stream updates.
- Rejudge controls for selected/problem/contest workflows.
- Clarification review and reply workflow.
- Scoreboard view with admin/public stream support, freeze state, and reveal controls.
- Hybrid oracle/generated-test panel:
  - tabbed problem details separate statement preview, test cases, prompt exports, engineering configuration, generated tests, and counterexamples;
  - prompt exports use Recommended Admin, Public/Safe, and Custom Advanced modes with supported-language selection;
  - configure reference solution, input generator, and optional input validator;
  - generate candidate tests without requiring a submission ID;
  - promote one, selected, or all valid generated cases to hidden official tests;
  - optionally run counterexample search for a specific submission;
  - promote counterexamples.

### Team

- Active-contest workspace.
- Problem sidebar.
- Code editor and language selection.
- Local draft persistence.
- Submissions and live submission stream refresh.
- Public/team scoreboard.
- Clarification submission and answer viewing.
- Public/sample test-case visibility only; hidden inputs and expected outputs are not exposed.

## Known UI Gaps

- Security monitoring screen is a placeholder.
- Quick statistics cards do not have backend aggregate endpoints yet.
- Problem engineering UI favors progressive disclosure; very large generated-test batches may still need deeper server-side pagination later.
- There is no announcements UI.
- There is no explicit contest enrollment/join workflow.
