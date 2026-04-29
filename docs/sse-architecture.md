# SSE Architecture

## Overview

The SSE layer is split into two tiers: a **shared infrastructure** that knows nothing about contests, and **audience-specific adapters** that connect the contest domain to that infrastructure. This separation means adding a new real-time feature — team submission updates, jury notifications, etc. — requires writing a new adapter, not touching the core.

---

## Layer diagram

```
Browser clients
      │
      │  GET /api/contest/stream
      │  Authorization: Bearer <token>
      ▼
ContestStreamController          ← HTTP entry point, one per audience
      │
      │  snapshot on connect
      ▼
ContestSseRegistry               ← audience-specific bean (extends SseEmitterRegistry)
      │
      │  broadcastAll / publishTo / keepAliveAll
      ▼
SseEmitterRegistry               ← shared connection store + send logic
      ▲
      │  keepAliveAll every 15s
SseHeartbeatScheduler            ← shared, collects all registries automatically


Domain side:

ContestService / ContestStatusSyncService
      │
      │  publishes ContestUpdatedEvent (after tx commit)
      ▼
ContestSseAdapter                ← @TransactionalEventListener
      │
      │  ssePublisher.publish("contest-update", payload, contestSseRegistry)
      ▼
SsePublisher                     ← stateless facade, wraps event construction
      │
      ▼
ContestSseRegistry → SseEmitterRegistry → all connected clients
```

---

## File by file

### `SseEmitterRegistry` (shared)

The connection store. Owns two collections:

- `emitters` — broadcast clients, receive every `broadcastAll()` call
- `targetedEmitters` — a `Map<Long, List<SseEmitter>>`, keyed by an ID (contest ID, team ID, user ID, etc.), receive only `publishTo(id, ...)` calls

Not a Spring bean itself — concrete subclasses are the beans. This lets `SseHeartbeatScheduler` inject `List<SseEmitterRegistry>` and pick up every audience automatically.

Key methods:

| Method | What it does |
|--------|-------------|
| `register(emitter)` | Adds a broadcast client, wires lifecycle callbacks |
| `register(id, emitter)` | Adds a targeted client under an ID |
| `broadcastAll(event)` | Sends to every broadcast client |
| `publishTo(id, event)` | Sends to every client registered under that ID |
| `keepAliveAll(event)` | Sends to both broadcast and targeted clients |
| `safeBroadcastSend(...)` | Send + cleanup, returns boolean, touches only broadcast list |
| `safeTargetedSend(id, ...)` | Send + cleanup, returns boolean, touches only that ID's bucket |

Thread safety: `CopyOnWriteArrayList` for safe concurrent iteration, `ConcurrentHashMap.computeIfAbsent` for atomic bucket creation, per-emitter `synchronized` block so two threads can write to different clients in parallel.

---

### `SseHeartbeatScheduler` (shared)

Runs every 15 seconds. Sends a named `ping` event (not an SSE comment) to every client in every registry.

Two reasons it uses a named event instead of a comment:

1. `@microsoft/fetch-event-source` silently drops comment lines before `onmessage` fires — a comment keepalive would be invisible to the client-side watchdog
2. Named events reach `onmessage`, which resets the 30-second watchdog timer on the frontend

Spring auto-collects every `SseEmitterRegistry` bean into the injected `List<SseEmitterRegistry>` — adding a new audience requires no change here.

---

### `SsePublisher` (shared)

Stateless facade. Wraps SSE event construction (`SseEmitter.event().name(...).data(...)`) so callers don't repeat boilerplate. Receives the target registry as a parameter, which means one publisher bean serves every audience.

| Method | What it does |
|--------|-------------|
| `publish(name, payload, registry)` | Broadcast to all clients of that registry |
| `publishTo(id, name, payload, registry)` | Send only to clients registered under that ID |

---

### `ContestSseRegistry` (contest domain)

A marker subclass of `SseEmitterRegistry`. Empty class body — its only purpose is to be a distinct Spring bean so the contest domain has its own connection store, separate from any future audience (team, jury, etc.).

Renamed from `AdminSseRegistry` because the stream is consumed by both admin dashboards and team workspaces.

---

### `ContestSseAdapter` (contest domain)

The bridge between the contest domain and the SSE infrastructure. Listens for `ContestUpdatedEvent` (fired after every contest lifecycle transition) and pushes a `contest-update` SSE event to all connected clients.

`@TransactionalEventListener(fallbackExecution = true)` means it fires after the transaction commits — clients never receive an event for a change that was rolled back. `fallbackExecution = true` allows it to fire in tests or non-transactional contexts.

The payload is typed as `ContestSsePayload(String reason, ContestResponse snapshot)` — not `Object` — so the wire format is explicit.

---

### `ContestStreamController` (contest domain)

HTTP entry point for the SSE stream. Two responsibilities:

1. Send the initial full snapshot the moment a client connects (so they don't have to wait for the next event)
2. Register the emitter in `ContestSseRegistry` for future broadcasts

Order matters: `safeBroadcastSend` is called before `register`. If the initial snapshot send fails, the emitter is already completed — the controller short-circuits and never registers a dead emitter. This prevents a heartbeat or broadcast reaching a client that never received its initial state.

Protected with `@PreAuthorize("hasAnyRole('ADMIN', 'TEAM')")` — both audiences connect to the same stream.

---

### `useContestStream.ts` (frontend)

React hook that manages the SSE connection on the client side. Uses `@microsoft/fetch-event-source` instead of native `EventSource` because native `EventSource` cannot send custom headers — and the stream now requires `Authorization: Bearer <token>`.

Key behaviors:

**401 handling** — if the stream gets a 401 on connect, the hook calls `ensureRefreshedOnce()` (the same coalesced refresh helper used by all REST calls, preventing two concurrent `/auth/refresh` requests) and reconnects once with the new token. If no refresh is possible, it stops retrying and falls closed.

**Exponential backoff** — on network drops, `onerror` returns a delay (`1s → 2s → 4s → … capped at 30s`) and the library reconnects automatically.

**Client-side watchdog** — a 30-second timer that resets on every `onmessage` frame (including `ping` keepalives). If nothing arrives for 30 seconds — which is impossible in a healthy connection since the server sends pings every 15s — the watchdog aborts the connection and forces a reconnect. This catches silent proxy timeouts where `onerror` never fires but frames have stopped flowing.

**`openWhenHidden: true`** — keeps the stream alive when the browser tab is backgrounded, avoiding a full snapshot replay every time the admin returns to the tab.

Public API is `{ connectionState }` in, `{ onSnapshot, onContestUpdate }` out — unchanged from the original `EventSource` implementation.

---

## Connection lifecycle

```
Client connects
      │
      ▼
ContestStreamController.stream()
      │
      ├── safeBroadcastSend(snapshot) → fails → return dead emitter (never registered)
      │
      └── succeeds → contestSseRegistry.register(emitter)
                           │
                           ▼
                     Client is now live
                           │
                     ┌─────┴──────────────────────┐
                     │                            │
               Contest transitions          Every 15s
                     │                            │
                     ▼                            ▼
             ContestUpdatedEvent          SseHeartbeatScheduler
                     │                            │
                     ▼                            ▼
          ContestSseAdapter             keepAliveAll("ping")
                     │                            │
                     ▼                            ▼
          ssePublisher.publish()        all broadcast + targeted
                     │                  clients receive ping frame
                     ▼
          contestSseRegistry.broadcastAll()
                     │
                     ▼
          all connected clients receive contest-update
```

---

## How to extend for a new audience

The shared layer is already built for this. Adding a new real-time feature — for example, live submission verdicts for teams — requires four steps and touches zero shared files.

### Step 1 — New registry bean

```java
@Component
public class TeamSseRegistry extends SseEmitterRegistry { }
// That's the whole file.
// SseHeartbeatScheduler picks it up automatically via List<SseEmitterRegistry>.
```

### Step 2 — New controller endpoint

```java
@PreAuthorize("hasRole('TEAM')")
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(TIMEOUT);

    // send initial snapshot
    boolean sent = teamSseRegistry.safeBroadcastSend(emitter,
        SseEmitter.event().name("snapshot").data(buildSnapshot(), APPLICATION_JSON));
    if (!sent) return emitter;

    teamSseRegistry.register(emitter);
    return emitter;
}
```

Or for per-team targeted events, register under the team's ID:

```java
teamSseRegistry.register(teamId, emitter);
```

### Step 3 — New adapter

```java
@Component
@RequiredArgsConstructor
public class SubmissionSseAdapter {

    private final SsePublisher ssePublisher;
    private final TeamSseRegistry teamSseRegistry;

    @TransactionalEventListener(fallbackExecution = true)
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        // targeted — only the submitting team receives this
        ssePublisher.publishTo(
            event.teamId(),
            "submission-result",
            new SubmissionSsePayload(event.submissionId(), event.verdict()),
            teamSseRegistry
        );
    }

    public record SubmissionSsePayload(Long submissionId, String verdict) { }
}
```

### Step 4 — Frontend hook (reuse or extend `useContestStream`)

`useContestStream.ts` lives in `UI/src/hooks/` (shared). For a team workspace pointing at a different endpoint, either pass the URL as a parameter or create a thin wrapper:

```ts
export function useTeamStream(handlers: TeamStreamHandlers) {
    // same pattern, different endpoint and event names
}
```

### What you do NOT need to change

- `SseEmitterRegistry` — no changes
- `SseHeartbeatScheduler` — no changes, auto-discovers the new registry
- `SsePublisher` — no changes
- `ContestSseAdapter` — no changes
- Any existing contest stream files — no changes

---

## Deferred improvements

Two items were intentionally left out of the current implementation and should be tracked for later:

**Hook relocation** — `useContestStream.ts` imports from `../admin/services/api` and `../admin/types/api`. When the team workspace is built, those dependencies should also move out of `admin/` into a shared location. The hook move is done; the service/types split is a follow-up.

**REST polling fallback** — `ContestOverview.tsx` polls all four REST endpoints every 10 seconds when `connectionState === 'closed'`. This is a safety net that predates the watchdog. With the watchdog now catching silent drops and reconnecting, the polling interval could be relaxed or the fallback removed entirely once the watchdog has been validated in production.
