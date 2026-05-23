# AuraC2 Documentation Archive Index

This archive contains historical documentation, old report drafts, duplicate exported PDFs, and task prompts. These files are kept for traceability only. They are not the current implementation source of truth.

Current documentation source of truth:

- `README.md`
- `UI/README.md`
- `docs/report-rebuild/analysis-plan.md`
- `docs/report-rebuild/report-draft-google-docs.md`
- `docs/report-rebuild/diagram-prompts.md`

## Legacy Markdown

| Moved file | Old path | New archive path | Reason | Superseded by | Unique content preserved elsewhere | Historical only |
|---|---|---|---|---|---|---|
| `CLAUDE.md` | `CLAUDE.md` | `docs/archive/legacy-markdown/CLAUDE.md` | Agent guidance contained stale paths and outdated feature claims. | `README.md`, `docs/report-rebuild/*` | Current commands and feature truth are in active docs. | Yes |
| `DEPLOYMENT.md` | `DEPLOYMENT.md` | `docs/archive/legacy-markdown/DEPLOYMENT.md` | Claimed full Docker Compose deployment as current even though backend/frontend services are commented out. | `README.md` | Correct Docker truth and setup steps are now in `README.md`. | Yes |
| `DESIGN.md` | `DESIGN.md` | `docs/archive/legacy-markdown/DESIGN.md` | Older design notes are superseded by the report rebuild. | `docs/report-rebuild/report-draft-google-docs.md` | Verified architecture is summarized in active report docs. | Yes |
| `DOCKER_README.md` | `DOCKER_README.md` | `docs/archive/legacy-markdown/DOCKER_README.md` | Outdated deployment summary overstated active compose services and old DDL guidance. | `README.md` | Correct compose and migration workflow are now in `README.md`. | Yes |
| `README_MERGED_SRC.txt` | `UI/README_MERGED_SRC.txt` | `docs/archive/legacy-markdown/README_MERGED_SRC.txt` | Old merged-frontend helper note with stale integration wording. | `UI/README.md` | Current frontend workflows and commands are in `UI/README.md`. | Yes |
| `SYSTEM_DOCS.md` | `SYSTEM_DOCS.md` | `docs/archive/legacy-markdown/SYSTEM_DOCS.md` | Legacy system documentation duplicated and conflicted with current report rebuild. | `docs/report-rebuild/*` | Current verified status is in active report docs. | Yes |

## Old Report Drafts

| Moved file | Old path | New archive path | Reason | Superseded by | Unique content preserved elsewhere | Historical only |
|---|---|---|---|---|---|---|
| `FULL_SYSTEM_AUDIT_REPORT.md` | `FULL_SYSTEM_AUDIT_REPORT.md` | `docs/archive/old-report-drafts/FULL_SYSTEM_AUDIT_REPORT.md` | Old audit predates later security, judging, migration, and oracle phases. | `docs/report-rebuild/analysis-plan.md` | Verified findings were reclassified in the analysis plan. | Yes |
| `BUGFIX_SCOREBOARD_REVEAL_PHASES_1_TO_4_REPORT.md` | `BUGFIX_SCOREBOARD_REVEAL_PHASES_1_TO_4_REPORT.md` | `docs/archive/old-report-drafts/BUGFIX_SCOREBOARD_REVEAL_PHASES_1_TO_4_REPORT.md` | Phase-specific fix report is no longer the main documentation. | `docs/report-rebuild/*` | Implemented scoreboard/reveal status is in active docs. | Yes |
| `BUGFIX_EDITOR_CONTEST_PROBLEM_PHASES_5_TO_7_REPORT.md` | `BUGFIX_EDITOR_CONTEST_PROBLEM_PHASES_5_TO_7_REPORT.md` | `docs/archive/old-report-drafts/BUGFIX_EDITOR_CONTEST_PROBLEM_PHASES_5_TO_7_REPORT.md` | Phase-specific fix report is superseded by current implementation docs. | `docs/report-rebuild/*` | Verified editor/problem/judging status is in active docs. | Yes |
| `phase-1-to-4-verification-report.md` | `docs/phase-1-to-4-verification-report.md` | `docs/archive/old-report-drafts/phase-1-to-4-verification-report.md` | Verification report is historical and predates later phases. | `docs/report-rebuild/analysis-plan.md` | Current verified findings are reflected in active analysis. | Yes |

## Source Material

These files contain useful historical implementation notes, but they are no longer current source-of-truth documents.

| Moved file | Old path | New archive path | Reason | Superseded by | Unique content preserved elsewhere | Historical only |
|---|---|---|---|---|---|---|
| `AuraC2_Clarifications_System_Architecture_Technical_Documentation.md` | `docs/AuraC2_Clarifications_System_Architecture_Technical_Documentation.md` | `docs/archive/source-material/AuraC2_Clarifications_System_Architecture_Technical_Documentation.md` | Standalone clarification architecture note is superseded by current report sections. | `docs/report-rebuild/*` | Clarification backend/UI status is summarized in active docs. | Yes |
| `AuraC2_Endpoints_Update_Architecture_Technical_Documentation.md` | `docs/AuraC2_Endpoints_Update_Architecture_Technical_Documentation.md` | `docs/archive/source-material/AuraC2_Endpoints_Update_Architecture_Technical_Documentation.md` | Endpoint update note is historical and may conflict with current routes. | `docs/report-rebuild/analysis-plan.md` | Current route evidence is listed in active analysis. | Yes |
| `aura-system-full-documentation.md` | `docs/aura-system-full-documentation.md` | `docs/archive/source-material/aura-system-full-documentation.md` | Large legacy system document duplicates current report rebuild. | `docs/report-rebuild/report-draft-google-docs.md` | Verified architecture and limitations are in active report draft. | Yes |
| `backend-full-system-documentation.md` | `docs/backend-full-system-documentation.md` | `docs/archive/source-material/backend-full-system-documentation.md` | Detailed backend notes are useful history but not the final truth after later phases. | `docs/report-rebuild/analysis-plan.md` | Current backend evidence is captured in feature tables. | Yes |
| `contest_lifecycle_guide.md` | `docs/contest_lifecycle_guide.md` | `docs/archive/source-material/contest_lifecycle_guide.md` | Standalone guide is superseded by report lifecycle sections. | `docs/report-rebuild/*` | Lifecycle behavior is preserved in active docs and diagrams. | Yes |
| `rejudge-feature-documentation.md` | `docs/rejudge-feature-documentation.md` | `docs/archive/source-material/rejudge-feature-documentation.md` | Standalone rejudge doc is superseded by report rebuild and README. | `README.md`, `docs/report-rebuild/*` | Rejudge behavior is summarized in active docs. | Yes |
| `scoreboard-feature-documentation.md` | `docs/scoreboard-feature-documentation.md` | `docs/archive/source-material/scoreboard-feature-documentation.md` | Standalone scoreboard doc is superseded by active report and README. | `README.md`, `docs/report-rebuild/*` | Scoreboard/freeze/reveal behavior is summarized in active docs. | Yes |
| `sse-architecture.md` | `docs/sse-architecture.md` | `docs/archive/source-material/sse-architecture.md` | SSE details are historical and now span contest, submission, clarification, and scoreboard streams. | `docs/report-rebuild/*` | Current SSE coverage is in active docs. | Yes |
| `team_landing_sse_logic.md` | `docs/team_landing_sse_logic.md` | `docs/archive/source-material/team_landing_sse_logic.md` | Team SSE note is historical and too narrow for current UI. | `UI/README.md`, `docs/report-rebuild/*` | Current team workflows are documented in active docs. | Yes |

## Task Prompts

| Moved file | Old path | New archive path | Reason | Superseded by | Unique content preserved elsewhere | Historical only |
|---|---|---|---|---|---|---|
| `TASKS_ENDPOINTS_AND_CLARIFICATIONS_FULL_DOCUMENTATION.md` | `docs/TASKS_ENDPOINTS_AND_CLARIFICATIONS_FULL_DOCUMENTATION.md` | `docs/archive/task-prompts/TASKS_ENDPOINTS_AND_CLARIFICATIONS_FULL_DOCUMENTATION.md` | Task-oriented documentation should not be mixed with final docs. | `docs/report-rebuild/*` | Current endpoint and clarification status is summarized in active docs. | Yes |
| `thetask1-force-rejudge-full-documentation.md` | `docs/thetask1-force-rejudge-full-documentation.md` | `docs/archive/task-prompts/thetask1-force-rejudge-full-documentation.md` | Task-specific force-rejudge note is historical. | `README.md`, `docs/report-rebuild/*` | Current rejudge behavior is documented in active docs. | Yes |
| `thetaskpromt.txt` | `docs/thetaskpromt.txt` | `docs/archive/task-prompts/thetaskpromt.txt` | Raw task prompt is historical and not implementation truth. | `docs/report-rebuild/*` | Relevant verified behavior is documented in active docs. | Yes |

## Legacy PDFs

| Moved file | Old path | New archive path | Reason | Superseded by | Unique content preserved elsewhere | Historical only |
|---|---|---|---|---|---|---|
| `AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/archive/legacy-pdfs/root-docs/AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | Duplicate/legacy exported report PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Active report draft preserves verified structure. | Yes |
| `AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/archive/legacy-pdfs/root-docs/AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | Duplicate/legacy exported report PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Active report draft preserves verified structure. | Yes |
| `AuraC#U00b2  Functional Requirements .pdf` | `docs/AuraC#U00b2  Functional Requirements .pdf` | `docs/archive/legacy-pdfs/root-docs/AuraC#U00b2  Functional Requirements .pdf` | Duplicate/legacy requirements PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Current requirements table is in active report draft. | Yes |
| `AuraC#U00b2 #U2013 Aura Contest Control (1).pdf` | `docs/AuraC#U00b2 #U2013 Aura Contest Control (1).pdf` | `docs/archive/legacy-pdfs/root-docs/AuraC#U00b2 #U2013 Aura Contest Control (1).pdf` | Duplicate/legacy report PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Current report content is in active report draft. | Yes |
| `AuraC^2SystemFlow.drawio (1).pdf` | `docs/AuraC^2SystemFlow.drawio (1).pdf` | `docs/archive/legacy-pdfs/root-docs/AuraC^2SystemFlow.drawio (1).pdf` | Old exported system-flow PDF. | `docs/report-rebuild/diagram-prompts.md` | Current diagram prompts preserve verified flows. | Yes |
| `AuraC² – Aura Contest Control (1).pdf` | `docs/AuraC² – Aura Contest Control (1).pdf` | `docs/archive/legacy-pdfs/root-docs/AuraC² – Aura Contest Control (1).pdf` | Duplicate/legacy report PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Current report content is in active report draft. | Yes |
| `AuraC²  Functional Requirements .pdf` | `docs/AuraC²  Functional Requirements .pdf` | `docs/archive/legacy-pdfs/root-docs/AuraC²  Functional Requirements .pdf` | Duplicate/legacy requirements PDF. | `docs/report-rebuild/report-draft-google-docs.md` | Current requirements table is in active report draft. | Yes |
| `AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/digrams/AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/archive/legacy-pdfs/digrams/AURAC#U00b2 #U2013 AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | Old diagram/report export in legacy diagram folder. | `docs/report-rebuild/diagram-prompts.md` | Current diagrams are PlantUML prompts in active docs. | Yes |
| `AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/digrams/AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | `docs/archive/legacy-pdfs/digrams/AURAC² – AURA CONTEST CONTROL_ SYSTEM DESIGN AND INITIAL IMPLEMENTATION (1) (1).pdf` | Old diagram/report export in legacy diagram folder. | `docs/report-rebuild/diagram-prompts.md` | Current diagrams are PlantUML prompts in active docs. | Yes |
