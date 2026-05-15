import { useEffect, useRef, useState } from "react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import type { ScoreboardSnapshot, ScoreboardUpdatePayload } from "../admin/types/api";
import { ensureRefreshedOnce, getAccessToken } from "../admin/services/api";

export type ScoreboardStreamConnectionState = "connecting" | "open" | "closed";
export type ScoreboardStreamRole = "ADMIN" | "PUBLIC";

type ScoreboardStreamHandlers = {
  contestId: number | null;
  role: ScoreboardStreamRole;
  enabled?: boolean;
  snapshotVersion?: number;
  onSnapshot?: (snapshot: ScoreboardSnapshot) => void;
  onUpdate?: (payload: ScoreboardUpdatePayload) => void;
  onFreeze?: (payload: ScoreboardUpdatePayload) => void;
  onRevealStep?: (payload: ScoreboardUpdatePayload) => void;
  onVersionGap?: () => void;
  onError?: (error: unknown) => void;
};

const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

const MAX_BACKOFF_MS = 30_000;
const BASE_BACKOFF_MS = 1000;
const WATCHDOG_TIMEOUT_MS = 30_000;

function streamUrl(path: string): string {
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

function endpointFor(role: ScoreboardStreamRole, contestId: number): string {
  return role === "ADMIN"
    ? `/api/admin/scoreboard/contests/${contestId}/stream`
    : `/api/scoreboard/contests/${contestId}/stream`;
}

function isScoreboardSnapshot(value: unknown): value is ScoreboardSnapshot {
  if (!value || typeof value !== "object") return false;
  const snapshot = value as Partial<ScoreboardSnapshot>;
  return Boolean(snapshot.metadata && Array.isArray(snapshot.rows));
}

function isScoreboardPayload(value: unknown): value is ScoreboardUpdatePayload {
  if (!value || typeof value !== "object") return false;
  const payload = value as Partial<ScoreboardUpdatePayload>;
  return (
    typeof payload.eventType === "string" &&
    typeof payload.contestId === "number" &&
    typeof payload.version === "number" &&
    Boolean(payload.metadata) &&
    Array.isArray(payload.changedRows)
  );
}

class FatalSseError extends Error {}
class RetryAfterRefreshError extends Error {}

export function useScoreboardStream({
  contestId,
  role,
  enabled = true,
  snapshotVersion,
  onSnapshot,
  onUpdate,
  onFreeze,
  onRevealStep,
  onVersionGap,
  onError,
}: ScoreboardStreamHandlers): { connectionState: ScoreboardStreamConnectionState } {
  const [connectionState, setConnectionState] =
    useState<ScoreboardStreamConnectionState>("closed");

  const handlersRef = useRef({
    onSnapshot,
    onUpdate,
    onFreeze,
    onRevealStep,
    onVersionGap,
    onError,
  });
  handlersRef.current = {
    onSnapshot,
    onUpdate,
    onFreeze,
    onRevealStep,
    onVersionGap,
    onError,
  };

  const versionRef = useRef<number>(snapshotVersion ?? 0);
  useEffect(() => {
    if (typeof snapshotVersion === "number") {
      versionRef.current = snapshotVersion;
    }
  }, [snapshotVersion]);

  useEffect(() => {
    if (!enabled || contestId == null) {
      setConnectionState("closed");
      return;
    }

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
        if (!cancelled) controller.abort();
      }, WATCHDOG_TIMEOUT_MS);
    };

    const handlePayload = (eventName: string, data: string) => {
      const parsed = JSON.parse(data);
      if (!isScoreboardPayload(parsed)) return;

      const previous = versionRef.current;
      if (previous > 0 && parsed.version > previous + 1) {
        handlersRef.current.onVersionGap?.();
      }
      versionRef.current = Math.max(versionRef.current, parsed.version);

      if (eventName === "scoreboard-freeze") {
        handlersRef.current.onFreeze?.(parsed);
        return;
      }
      if (eventName === "scoreboard-reveal-step") {
        handlersRef.current.onRevealStep?.(parsed);
        return;
      }
      handlersRef.current.onUpdate?.(parsed);
    };

    const connect = async (): Promise<void> => {
      if (cancelled) return;

      controller = new AbortController();
      setConnectionState("connecting");

      try {
        await fetchEventSource(streamUrl(endpointFor(role, contestId)), {
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

            throw new FatalSseError(`unexpected stream response ${response.status}`);
          },

          onmessage: (event) => {
            armWatchdog();
            if (event.event === "ping") return;

            try {
              if (event.event === "snapshot") {
                const parsed = JSON.parse(event.data);
                if (!isScoreboardSnapshot(parsed)) return;
                versionRef.current = parsed.metadata.version;
                handlersRef.current.onSnapshot?.(parsed);
                return;
              }

              if (
                event.event === "scoreboard-update" ||
                event.event === "scoreboard-freeze" ||
                event.event === "scoreboard-reveal-step"
              ) {
                handlePayload(event.event, event.data);
              }
            } catch (error) {
              handlersRef.current.onError?.(error);
            }
          },

          onclose: () => {
            if (!cancelled) setConnectionState("closed");
            throw new Error("stream closed by server");
          },

          onerror: (error) => {
            clearWatchdog();
            if (cancelled || error instanceof FatalSseError) {
              setConnectionState("closed");
              handlersRef.current.onError?.(error);
              throw error;
            }

            if (error instanceof RetryAfterRefreshError) {
              throw error;
            }

            setConnectionState("connecting");
            handlersRef.current.onError?.(error);
            const delay = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS * 2 ** attempt);
            attempt += 1;
            return delay;
          },
        });
      } catch (error) {
        if (cancelled) return;

        if (error instanceof RetryAfterRefreshError) {
          attempt = 0;
          await connect();
          return;
        }

        setConnectionState("closed");
        handlersRef.current.onError?.(error);
      }
    };

    void connect();

    return () => {
      cancelled = true;
      clearWatchdog();
      controller.abort();
    };
  }, [contestId, enabled, role]);

  return { connectionState };
}
