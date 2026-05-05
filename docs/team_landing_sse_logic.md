# Team Landing Page and SSE Logic

This document describes the team-facing lifecycle flow added on this branch:

- How the team page decides what to show.
- How the team SSE stream keeps the page live.
- How countdowns work for upcoming, running, paused, and ended contests.

It intentionally focuses on product and lifecycle behavior, not stream error handling.

---

## Branch Goal

The team UI should behave like an ICPC-style contest system:

- Teams interact with the system for the current contest only.
- Old ended contests are archive/history for admins, not a default team landing state.
- If there is no upcoming, running, or paused contest, the team should see a neutral "no active contest" screen.
- If a contest starts, pauses, resumes, or ends while the team is connected, the team UI should move live without refresh.

---

## Main Files

Frontend:

- `UI/src/team/App.tsx`
  - Owns the team lifecycle state.
  - Connects to `/api/team/stream`.
  - Decides whether to render the landing page or workspace.

- `UI/src/team/components/TeamLandingPage.tsx`
  - Shows pre-contest, paused, ended, or no-contest states.
  - Handles upcoming and paused countdown display.

- `UI/src/team/TeamWorkspace.tsx`
  - Shows the active contest workspace.
  - Passes the running contest end time into the header.

- `UI/src/team/components/Header.tsx`
  - Shows the live running countdown.

- `UI/src/hooks/useContestStream.ts`
  - Shared SSE hook.
  - Defaults to `/api/contest/stream` for admin.
  - Accepts `/api/team/stream` for team clients.

Backend:

- `TeamStreamController`
  - Creates the team SSE connection.
  - Sends the initial snapshot.
  - Registers the team emitter under the team user id.

- `TeamSseRegistry`
  - Stores team SSE connections.
  - Uses targeted buckets keyed by team id.

- `TeamContestSseAdapter`
  - Mirrors contest lifecycle updates to every connected team.

- `ContestLifecycleService`
  - Computes effective state, effective end time, remaining time, and pause-aware countdown values.

---

## Team State Model

The team app stores one resolved state:

```ts
type ResolvedState =
  | {
      lifecycle: "RUNNING" | "UPCOMING" | "PAUSED" | "ENDED";
      contest: ContestResponse;
    }
  | { lifecycle: "NONE"; contest: null };
```

The UI is simple after that:

| Lifecycle | Team UI |
|-----------|---------|
| `RUNNING` | Full team workspace |
| `UPCOMING` | Landing page with countdown to start |
| `PAUSED` | Landing page with frozen remaining time |
| `ENDED` | Landing page saying contest has ended |
| `NONE` | Landing page saying no active contest |

---

## Cold Load Logic

When a team first logs in, `TeamApp` performs a REST probe:

1. Try active contest.
2. Try upcoming contest.
3. Try paused contest.
4. If all fail, return `NONE`.

Ended contests are not probed on cold load. This is intentional.

Reason: if the system only contains old ended contests, a team should not be greeted by last week's contest. They should see "No active contest" instead.

---

## Initial SSE Snapshot Logic

After the page opens, the team connects to:

```text
GET /api/team/stream
```

The backend immediately sends a `snapshot` event containing:

```ts
{
  active: ContestResponse | null;
  upcoming: ContestResponse | null;
  paused: ContestResponse | null;
  ended: ContestResponse[];
}
```

The team frontend resolves the snapshot in priority order:

1. `active` -> `RUNNING`
2. `paused` -> `PAUSED`
3. `upcoming` -> `UPCOMING`
4. otherwise -> `NONE`

`ended` is ignored in the snapshot path.

That is the key team rule: the ended list is an archive bucket. It should not choose an old contest for a team on login.

---

## Live SSE Update Logic

After the initial snapshot, lifecycle changes arrive as `contest-update` events:

```ts
{
  reason: "CREATED" | "MANUAL_START" | "AUTO_START" | "MANUAL_PAUSE" |
          "MANUAL_RESUME" | "MANUAL_END" | "AUTO_END";
  snapshot: ContestResponse;
}
```

The team app maps each reason to a lifecycle:

| SSE reason | Team lifecycle | UI result |
|------------|----------------|-----------|
| `CREATED` | `UPCOMING` | Show landing page before contest |
| `MANUAL_START` | `RUNNING` | Enter workspace immediately |
| `AUTO_START` | `RUNNING` | Enter workspace at scheduled start |
| `MANUAL_PAUSE` | `PAUSED` | Leave workspace and show paused page |
| `MANUAL_RESUME` | `RUNNING` | Return to workspace |
| `MANUAL_END` | `ENDED` | Show contest ended page |
| `AUTO_END` | `ENDED` | Show contest ended page |

This means ended contests are only shown to teams when the current contest ends live. Old ended contests from the snapshot archive are not used.

---

## Countdown Data Fields

The countdown behavior depends on four backend fields:

| Field | Meaning |
|-------|---------|
| `startTime` | Scheduled/planned start time |
| `actualStartTime` | When the contest clock actually started |
| `endTime` | Scheduled end time: `startTime + duration` |
| `effectiveEndTime` | Real running end time: `actualStartTime + duration + totalPauseMillis` |

`startTime` answers:

> When was this contest planned to start?

`actualStartTime` answers:

> When did the contest clock actually start?

For manually controlled contests, these may be different.

---

## Countdown By State

### Upcoming

When lifecycle is `UPCOMING`, the team landing page counts down to:

```ts
contest.startTime
```

This is not contest duration. It is the time until the contest becomes available.

Example:

```text
startTime: 10:00
duration: 120 minutes
current time: 09:45
```

Team sees:

```text
Before the contest
00:15:00
```

---

### Manual Start Before Scheduled Time

If admin starts the contest before `startTime`, backend stamps:

```java
actualStartTime = now
```

Then it sends:

```text
MANUAL_START
```

The team frontend switches immediately to `RUNNING`.

The running countdown uses:

```ts
contest.effectiveEndTime ?? contest.endTime
```

Because `effectiveEndTime` is based on `actualStartTime`, the clock starts from the manual start moment, not from the scheduled start.

Example:

```text
Scheduled startTime: 10:00
Admin clicks Start: 09:50
duration: 120 minutes
actualStartTime: 09:50
effectiveEndTime: 11:50
```

Team sees workspace immediately, with countdown to:

```text
11:50
```

So at `09:50`, the timer starts around:

```text
02:00:00
```

---

### Manual Start After Scheduled Time

If admin starts late, backend still stamps:

```java
actualStartTime = now
```

Example:

```text
Scheduled startTime: 10:00
Admin clicks Start: 10:07
duration: 120 minutes
actualStartTime: 10:07
effectiveEndTime: 12:07
```

Team sees workspace with countdown to:

```text
12:07
```

The team does not lose 7 minutes just because the scheduled time passed.

---

### Auto Start

If the contest starts automatically when `startTime` arrives, backend stamps:

```java
actualStartTime = startTime
```

This avoids tiny scheduler delays from changing the contest clock.

Example:

```text
startTime: 10:00
duration: 120 minutes
auto-start task fires: 10:00:02
actualStartTime: 10:00
effectiveEndTime: 12:00
```

Team sees workspace with countdown to:

```text
12:00
```

Not `12:00:02`.

---

### Running

When lifecycle is `RUNNING`, the team workspace header counts down to:

```ts
contest.effectiveEndTime ?? contest.endTime
```

Normal expected case:

```text
effectiveEndTime exists
```

So the timer is pause-aware and actual-start-aware.

Fallback case:

```text
effectiveEndTime is null
```

Then the frontend falls back to scheduled `endTime`. This keeps a timer visible if the backend response has not stamped or computed an effective end yet.

---

### Manual Pause

When admin pauses, backend stores:

```java
pausedAt = now
```

The team receives:

```text
MANUAL_PAUSE
```

The frontend switches to `PAUSED`.

The paused landing page does not run a live interval. It displays:

```ts
contest.remainingMillis
```

That value is frozen by the backend at the pause moment.

Example:

```text
Contest duration: 120 minutes
actualStartTime: 10:00
pause time: 10:45
elapsed before pause: 45 minutes
remainingMillis: 75 minutes
```

Team sees:

```text
Contest is paused
01:15:00
```

The number stays fixed while paused.

---

### Manual Resume

When admin resumes, backend computes how long the contest was paused:

```java
totalPauseMillis += now - pausedAt
pausedAt = null
```

Then it sends:

```text
MANUAL_RESUME
```

The frontend switches back to `RUNNING`.

The new `effectiveEndTime` is pushed later by the pause duration.

Example:

```text
actualStartTime: 10:00
duration: 120 minutes
paused at: 10:45
resumed at: 11:00
pause duration: 15 minutes
effectiveEndTime: 12:15
```

Team sees workspace countdown to:

```text
12:15
```

---

### Auto End

The backend auto-ends when:

```text
now >= effectiveEndTime
```

Then it sends:

```text
AUTO_END
```

The frontend switches to `ENDED`.

Example:

```text
actualStartTime: 10:00
duration: 120 minutes
totalPauseMillis: 15 minutes
effectiveEndTime: 12:15
current time: 12:15
```

Team sees:

```text
Contest has ended
```

---

### Manual End

When admin manually ends a contest, backend sends:

```text
MANUAL_END
```

The frontend switches to `ENDED`.

Team sees:

```text
Contest has ended
```

No countdown is shown in the ended state.

---

## Timeline Example

Contest:

```text
startTime: 10:00
duration: 120 minutes
```

Scenario:

```text
09:50 admin manually starts
10:35 admin pauses
10:50 admin resumes
12:05 contest auto-ends
```

Backend fields:

```text
actualStartTime: 09:50
pause duration: 15 minutes
effectiveEndTime: 09:50 + 120m + 15m = 12:05
```

Team UI:

| Time | Event | Team page |
|------|-------|-----------|
| 09:40 | Snapshot says upcoming | Landing page, countdown to 10:00 |
| 09:50 | `MANUAL_START` | Workspace, countdown to 11:50 initially |
| 10:35 | `MANUAL_PAUSE` | Paused landing page, frozen remaining time |
| 10:50 | `MANUAL_RESUME` | Workspace, new countdown to 12:05 |
| 12:05 | `AUTO_END` | Ended landing page |

---

## Summary

The team page has two sources of truth:

1. Initial snapshot/REST probe to decide what the team should see on entry.
2. Live SSE updates to move the team through the current contest lifecycle.

Countdown rules:

- `UPCOMING` counts down to `startTime`.
- `RUNNING` counts down to `effectiveEndTime`.
- `PAUSED` shows frozen `remainingMillis`.
- `ENDED` shows no countdown.
- `NONE` shows no countdown.

The main distinction is:

```text
startTime = planned start
actualStartTime = real contest clock start
effectiveEndTime = actualStartTime + duration + pauses
```

That distinction is what makes early manual starts, late manual starts, pauses, resumes, and auto-end all behave correctly for teams.
