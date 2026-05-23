# Contest Management Lifecycle — Conceptual Guide

Alright — this is a lot of moving parts, but there's one big idea underneath that makes everything else click. Let me walk you through it.

---

## The Core Idea: DB Is Always a Little Behind Reality

Your system has two views of "what state is the contest in?":

**Persisted state** (`contest.status` column): what's actually written in the database. Only changes when someone — a human via `ContestService.updateStatus`, or the sync machinery — explicitly writes a new value.

**Effective state** (`ContestLifecycleService.resolveEffectiveState`): what the contest *should* look like *right now*, computed on the fly from `persisted status + current clock + pause history`.

Why both? Because time moves continuously but database writes are discrete events. Picture a contest scheduled to start at 10:00:00. At 09:59:59 both views agree: `UPCOMING`. At 10:00:01 the clock has crossed the line but no scheduler thread has run yet — persisted is still `UPCOMING`, but effective is already `RUNNING`. A few milliseconds later the scheduler flips the DB and they agree again.

So the rules are:
- **Writes** go to persisted (commands, sync executor).
- **Reads that matter to the UI** go through effective state (`toResponse`, `getStreamSnapshot`, `findLatestContestByEffectiveState`).
- **The whole purpose of the two schedulers** is to close the gap between effective and persisted.

This is also why `resolveEffectiveEndTime` returns `null` when `PAUSED` — a paused contest has no wall-clock end, so "has it ended yet?" is a meaningless question until someone resumes.

---

## The Lifecycle, Transition by Transition

Every state change in the system flows through a consistent 4-step pattern:

1. **Validate + mutate the entity** (set timestamps, update status)
2. **Save to DB**
3. **Publish a `ContestUpdatedEvent`** (fires SSE after commit)
4. **`ContestTransitionScheduler` reacts to that event** (reschedule or cancel the parked task)

**CREATED** — Admin fills `CreateContestModal`, POSTs to `/api/contest`. `createContest` validates no other active contest exists, saves with status `UPCOMING`, publishes `CREATED`, and the scheduler parks a task at `startTime`.

**MANUAL_START** — `PUT /api/contest/{id}/start`. Guards against another running contest, stamps `actualStartTime = now`, saves as `RUNNING`, publishes `MANUAL_START`, and the scheduler reschedules the parked task to the `effectiveEndTime`.

**AUTO_START** — No admin action, clock passes `startTime`. Either the parked one-shot task fires, or the fallback scheduler picks it up. `ContestStatusSyncExecutor` row-locks the contest, sees effective is `RUNNING` while persisted is `UPCOMING`, and — here's a subtle choice — stamps `actualStartTime = scheduledStart` (not `now`). This keeps the UI's "Scheduled Start" and "Actual Start" matching, rather than showing a confusing 30-millisecond drift. Then publishes `AUTO_START`. The scheduler's `onContestUpdated` listener catches that event, re-fetches the freshly committed entity in its own `REQUIRES_NEW` transaction (to avoid Hibernate's L1 cache returning the stale pre-commit state), and immediately parks the next task for the end.

**MANUAL_PAUSE** — There's no auto-pause, ever. Admin-only. Sets `pausedAt = now`, saves, publishes `MANUAL_PAUSE`, and the scheduler cancels the pending task (clock is frozen, no scheduled end exists).

**MANUAL_RESUME** — Admin-only. Accumulates the pause gap: `totalPauseMillis += (now - pausedAt)`, clears `pausedAt`, saves as `RUNNING`, publishes `MANUAL_RESUME`, and the scheduler reschedules with the new pause-aware `effectiveEndTime` (which is now pushed later by the total pause duration).

**MANUAL_END** — If `juryOverride=false`, validates that `now >= effectiveEndTime` (paused contests return `null` here, so ending a paused contest *requires* jury override). Saves as `ENDED`, publishes `MANUAL_END`, and the scheduler cancels any pending task.

**AUTO_END** — Same sync path as auto-start. Effective state becomes `ENDED` once `now >= effectiveEndTime`, executor applies it, publishes `AUTO_END`, scheduler cancels the (now-fired) pending task.

The state machine itself is enforced by `isValidTransition` in `ContestService` and mirrored by `isAllowedAutoTransition` in the executor — notice the executor's rules are *strictly narrower*: only `UPCOMING→RUNNING` and `RUNNING→ENDED`. Pause, resume, jury-override end all require a human.

---

## How Updates Reach the Browser

The SSE pipeline has three clean layers:

**Connection setup** — Browser's `useContestStream` hook opens an `EventSource` on `/api/contest/stream`. `ContestStreamController` creates an `SseEmitter` (30-min timeout), registers it with `ContestStreamBroadcaster`, and immediately sends a `snapshot` event containing the full current state. This means a freshly-connected client has the complete picture before any deltas arrive.

**The event bus in the middle** — Every mutation path (manual commands in `ContestService`, auto-transitions in `ContestStatusSyncService`) calls `eventPublisher.publishEvent(new ContestUpdatedEvent(...))`. `ContestStreamBroadcaster.onContestUpdated` listens via `@TransactionalEventListener`, which is the critical detail: it fires *after commit*, not when `publishEvent` is called. So if a transaction rolls back, the UI never hears about a change that didn't actually happen.

**Broadcast fanout** — The broadcaster holds a `CopyOnWriteArrayList<SseEmitter>` (thread-safe iteration under concurrent adds/removes). When an event arrives, it iterates every connected client and sends a `contest-update` event with the snapshot. Failed sends drop that emitter. A separate `@Scheduled` heartbeat every 15 s sends a comment-only event so idle connections don't get killed by proxies.

**Client side** — `useContestStream` has two handlers:
- `onSnapshot` → `applySnapshot` wipes and replaces all four buckets (`activeContest`, `upcomingContest`, `pausedContest`, `endedContests`). Used on initial connect and reconnect.
- `onContestUpdate` → `placeContest` removes the contest from every bucket by ID then puts it in the right one based on `effectiveState`. Then `switchTabForReason` flips the visible tab (e.g. `MANUAL_START` → `active`).

There are two REST fallbacks layered on top. A one-time 3-second timeout fires on mount — if no SSE snapshot arrives within that window, it fetches all four endpoints once and hydrates the UI. Separately, whenever `connectionState` flips to `closed`, a 10-second polling interval takes over, re-fetching all four endpoints until the stream recovers. The moment SSE reconnects and delivers a snapshot, `applySnapshot` overwrites whatever polling produced and the interval clears.

---

## The Two Schedulers and Why Both Exist

This is where most of the "chaos" feeling probably comes from, because there are two schedulers that look like they do the same thing. They don't — they're layered.

**`ContestTransitionScheduler` (primary, exact-time)** — This is the one doing the actual work under normal conditions. For each contest that has a known future transition, it parks a single `ScheduledFuture` at the *exact* target `Instant`:

- `UPCOMING` → schedule at `startTime`
- `RUNNING` → schedule at pause-aware `effectiveEndTime`
- `PAUSED` → no task (clock is frozen, no wall-clock target)
- `ENDED` → no task (terminal)

It is driven entirely by `ContestUpdatedEvent` — `onContestUpdated` is a `@TransactionalEventListener` that reacts after every committed mutation and calls `reschedule()` or `cancelPending()` depending on the reason. When the parked task fires, `triggerSyncAndChain` calls `syncAllEligibleContests()` and nothing more. The chaining — parking the *next* task after, say, an auto-start — happens through the same event path: the auto-start publishes `AUTO_START`, `onContestUpdated` receives it, re-fetches the freshly committed entity in a `REQUIRES_NEW` transaction (critical to bypass Hibernate's L1 cache), and calls `reschedule()` for the end time.

The `pendingTasks` map is a `ConcurrentHashMap`, but the public methods are `synchronized` because the map alone isn't enough — two threads could interleave `remove` + `put` and leak a stale `ScheduledFuture`. The monitor guards the higher-level invariant "at most one parked task per contest." Tasks themselves run on pool threads and never hold the monitor, so no deadlock.

In-memory `ScheduledFuture`s don't survive JVM restarts, so `@EventListener(ApplicationReadyEvent)` walks the DB on startup and reschedules everything.

**`ContestStatusSyncScheduler` (fallback)** — A plain `@Scheduled` method running every few seconds (configurable, disabled unless `contest.sync.enabled=true`). It exists for the edge cases:
- The app was down when a transition should have happened.
- Clock drift between nodes.
- A parked task threw an exception nobody noticed.
- A task was cancelled but never rescheduled due to some bug.

Both schedulers funnel into the exact same code: `ContestStatusSyncService.syncAllEligibleContests()` → `ContestStatusSyncExecutor.syncContestStatus()`.

**What the sync actually checks** — `syncAllEligibleContests` asks the repo for any contest in `UPCOMING` or `RUNNING` that might need syncing, grabs the first one, and hands it to the executor. The executor:

1. Acquires a row lock (`SELECT ... FOR UPDATE`) — so if the exact-time task and the fallback both fire at once, one blocks.
2. Runs in `REQUIRES_NEW` — a fresh transaction, isolated from any caller's. This is why the executor is a separate bean: Spring's AOP proxy only intercepts external calls, so if the `@Transactional` method lived in the same class it would be bypassed.
3. Computes effective state. If equal to persisted → no-op.
4. Checks `isAllowedAutoTransition` (only `UPCOMING→RUNNING` or `RUNNING→ENDED`).
5. Checks `statusLocked` flag (a kill switch for admin-held contests).
6. For auto-start: stamps `actualStartTime` from `scheduledStart`.
7. Writes the new status, returns what it changed to.

Then back up in `syncAllEligibleContests`, on successful transition, `publishAutoTransitionEvent` fires the `ContestUpdatedEvent(AUTO_START)` or `AUTO_END` — which flows through the SSE pipeline described above.

One subtle wiring detail: `ContestService` depends on `ContestTransitionScheduler`, which depends on `ContestStatusSyncService`, which depends on `ContestService`. Spring can't build that. The `@Lazy` annotation on `transitionScheduler` in `ContestService` tells Spring to inject a proxy instead of the real bean, breaking the cycle at context-init time. By the time any real call happens, everything is wired up.

---

## The Main Flow, Tied Together

Three actors are constantly interacting: **commands** (admin clicks or time passing), **state** (DB row + in-memory scheduled tasks), and **notifications** (events → SSE → browser).

Every state change, whether human- or clock-driven, passes through the same choke point — a transactional mutation in the service layer that saves, publishes, and triggers a scheduler reaction through the event. The dual schedulers ensure the clock-driven changes actually happen: the exact-time scheduler is precise and efficient, the fallback scheduler is the insurance policy. The `@TransactionalEventListener` guarantees the UI only hears about things that actually committed. And the effective-state function keeps every read honest in the gap between "time says this should have changed" and "the DB has noticed."

**One sentence to hold all this together: persisted state is what the DB says, effective state is what the clock says, and everything else in the system is machinery for keeping the two in sync and telling the browser when they change.**

If any particular piece still feels fuzzy — for example the pause math, the row-locking race, or the `REQUIRES_NEW` layering — let me know which one and I'll zoom in.
