import { useEffect, useRef, useState } from "react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import {
  ClarificationStreamEvent,
  ClarificationStreamEventType,
} from "../admin/types/api";
import {
  ensureRefreshedOnce,
  getAccessToken,
} from "../admin/services/api";

export type ClarificationStreamConnectionState = "connecting" | "open" | "closed";
export type ClarificationStreamRole = "TEAM" | "ADMIN";

type ClarificationStreamHandlers = {
  contestId: number | null;
  role: ClarificationStreamRole;
  enabled?: boolean;
  onEvent?: (event: ClarificationStreamEvent) => void;
  onError?: (error: unknown) => void;
};

const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

const MAX_BACKOFF_MS = 30_000;
const BASE_BACKOFF_MS = 1000;
const WATCHDOG_TIMEOUT_MS = 30_000;

const STREAM_EVENT_TYPES = new Set<ClarificationStreamEventType>([
  "CLARIFICATION_CREATED",
  "CLARIFICATION_REPLIED",
  "CLARIFICATION_PUBLIC_ANSWERED",
]);

function streamUrl(path: string): string {
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

function endpointFor(role: ClarificationStreamRole, contestId: number): string {
  if (role === "ADMIN") {
    return `/api/clarifications/admin/stream/${contestId}`;
  }
  return `/api/clarifications/my/stream/${contestId}`;
}

function isClarificationStreamEvent(value: unknown): value is ClarificationStreamEvent {
  if (!value || typeof value !== "object") return false;
  const event = value as Partial<ClarificationStreamEvent>;

  return (
    typeof event.type === "string" &&
    STREAM_EVENT_TYPES.has(event.type as ClarificationStreamEventType) &&
    typeof event.contestId === "number" &&
    typeof event.clarificationId === "number"
  );
}

class FatalSseError extends Error {}
class RetryAfterRefreshError extends Error {}

export function useClarificationStream({
  contestId,
  role,
  enabled = true,
  onEvent,
  onError,
}: ClarificationStreamHandlers): { connectionState: ClarificationStreamConnectionState } {
  const [connectionState, setConnectionState] =
    useState<ClarificationStreamConnectionState>("closed");

  const handlersRef = useRef({ onEvent, onError });
  handlersRef.current = { onEvent, onError };

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
        if (cancelled) return;
        controller.abort();
      }, WATCHDOG_TIMEOUT_MS);
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

            throw new FatalSseError(
              `unexpected stream response ${response.status} ${contentType}`
            );
          },

          onmessage: (event) => {
            armWatchdog();

            if (event.event === "ping") return;

            if (
              event.event !== "clarification-created" &&
              event.event !== "clarification-replied" &&
              event.event !== "clarification-public-answered"
            ) {
              return;
            }

            try {
              const parsed = JSON.parse(event.data);
              if (!isClarificationStreamEvent(parsed)) return;
              if (parsed.contestId !== contestId) return;
              handlersRef.current.onEvent?.(parsed);
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
            const delay = Math.min(
              MAX_BACKOFF_MS,
              BASE_BACKOFF_MS * 2 ** attempt
            );
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
