import { useEffect, useRef, useState } from "react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import type {
  ContestStreamSnapshot,
  ContestStreamUpdate,
} from "../admin/types/api";
import { ensureRefreshedOnce, getAccessToken } from "../admin/services/api";

export type ContestStreamConnectionState = "connecting" | "open" | "closed";

type ContestStreamHandlers = {
  onSnapshot?: (snapshot: ContestStreamSnapshot) => void;
  onContestUpdate?: (update: ContestStreamUpdate) => void;
};

const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

function streamUrl(path: string): string {
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

class FatalSseError extends Error {}
class RetryAfterRefreshError extends Error {}

const MAX_BACKOFF_MS = 30_000;
const BASE_BACKOFF_MS = 1000;

// 30s = 2× the server keepalive interval (15s, see SseHeartbeatScheduler).
// Missing two consecutive ping frames means the connection is silently dead
// (proxy half-close, NAT timeout, etc.) — the lib's onerror won't fire because
// the underlying stream still appears "open"; we have to abort and reconnect.
const WATCHDOG_TIMEOUT_MS = 30_000;

/**
 * Subscribes to a contest SSE stream and forwards `snapshot` / `contest-update`
 * events to the supplied handlers. The default endpoint is the admin stream
 * (/api/contest/stream); pass `/api/team/stream` to consume the team stream
 * instead. Both expose the same wire format.
 *
 * Uses @microsoft/fetch-event-source so we can attach `Authorization: Bearer ...`.
 * Includes a watchdog that detects silent connection drops by tracking time
 * since the last observed frame (any named event, including the server's
 * 15s "ping" keepalive).
 */
export function useContestStream(
  handlers: ContestStreamHandlers,
  endpoint = "/api/contest/stream"
): { connectionState: ContestStreamConnectionState } {
  const handlersRef = useRef<ContestStreamHandlers>(handlers);
  handlersRef.current = handlers;

  const [connectionState, setConnectionState] =
    useState<ContestStreamConnectionState>("connecting");

  useEffect(() => {
    let cancelled = false;
    let attempt = 0;
    let controller = new AbortController();
    let watchdogTimer: ReturnType<typeof setTimeout> | null = null;

    const clearWatchdog = () => {
      if (watchdogTimer !== null) {
        clearTimeout(watchdogTimer);
        watchdogTimer = null;
      }
    };

    const armWatchdog = () => {
      clearWatchdog();
      watchdogTimer = setTimeout(() => {
        if (cancelled) return;
        // Stream went silent — abort the dead connection and reconnect.
        // Aborting causes onerror to fire with the existing backoff path.
        controller.abort();
      }, WATCHDOG_TIMEOUT_MS);
    };

    const connect = async (): Promise<void> => {
      if (cancelled) return;

      controller = new AbortController();
      setConnectionState("connecting");

      try {
        await fetchEventSource(streamUrl(endpoint), {
          signal: controller.signal,
          openWhenHidden: true,
          headers: {
            Authorization: `Bearer ${getAccessToken() ?? ""}`,
          },

          onopen: async (response) => {
            if (cancelled) throw new FatalSseError("cancelled");

            const contentType = response.headers.get("content-type") ?? "";
            if (response.ok && contentType.includes("text/event-stream")) {
              attempt = 0;
              setConnectionState("open");
              armWatchdog();
              return;
            }

            if (response.status === 401) {
              const newToken = await ensureRefreshedOnce();
              if (newToken) throw new RetryAfterRefreshError();
              throw new FatalSseError("unauthorized");
            }

            throw new FatalSseError(
              `unexpected stream response ${response.status} ${contentType}`
            );
          },

          onmessage: (ev) => {
            // Any named event proves the connection is alive — reset the watchdog
            // before any per-event branching so an exception in a handler doesn't
            // skip the reset.
            armWatchdog();

            if (ev.event === "ping") return; // keepalive only — already counted

            if (ev.event === "snapshot") {
              try {
                const data = JSON.parse(ev.data) as ContestStreamSnapshot;
                handlersRef.current.onSnapshot?.(data);
              } catch (err) {
                console.warn("Failed to parse SSE snapshot payload", err);
              }
              return;
            }
            if (ev.event === "contest-update") {
              try {
                const data = JSON.parse(ev.data) as ContestStreamUpdate;
                handlersRef.current.onContestUpdate?.(data);
              } catch (err) {
                console.warn("Failed to parse SSE contest-update payload", err);
              }
            }
          },

          onclose: () => {
            if (!cancelled) setConnectionState("closed");
            throw new Error("stream closed by server");
          },

          onerror: (err) => {
            clearWatchdog();
            if (cancelled || err instanceof FatalSseError) {
              setConnectionState("closed");
              throw err;
            }
            if (err instanceof RetryAfterRefreshError) {
              throw err;
            }
            setConnectionState("connecting");
            const delay = Math.min(
              MAX_BACKOFF_MS,
              BASE_BACKOFF_MS * 2 ** attempt
            );
            attempt += 1;
            return delay;
          },
        });
      } catch (err) {
        if (cancelled) return;
        if (err instanceof RetryAfterRefreshError) {
          attempt = 0;
          await connect();
          return;
        }
        setConnectionState("closed");
      }
    };

    void connect();

    return () => {
      cancelled = true;
      clearWatchdog();
      controller.abort();
    };
  }, [endpoint]);

  return { connectionState };
}
