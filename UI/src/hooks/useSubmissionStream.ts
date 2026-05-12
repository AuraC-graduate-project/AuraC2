import { useEffect, useRef, useState } from "react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import type { SubmissionStreamEvent } from "../admin/types/api";
import {
  ensureRefreshedOnce,
  getAccessToken,
} from "../admin/services/api";

export type SubmissionStreamConnectionState = "connecting" | "open" | "closed";
export type SubmissionStreamRole = "TEAM" | "ADMIN";

type SubmissionStreamHandlers = {
  role: SubmissionStreamRole;
  enabled?: boolean;
  onEvent?: (event: SubmissionStreamEvent) => void;
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

function endpointFor(role: SubmissionStreamRole): string {
  return role === "ADMIN"
    ? "/api/admin/submissions/stream"
    : "/api/submissions/stream";
}

function isSubmissionStreamEvent(value: unknown): value is SubmissionStreamEvent {
  if (!value || typeof value !== "object") return false;
  const e = value as Partial<SubmissionStreamEvent>;
  return (
    typeof e.eventType === "string" &&
    typeof e.submissionId === "number" &&
    typeof e.contestId === "number" &&
    typeof e.problemId === "number" &&
    typeof e.userId === "number"
  );
}

class FatalSseError extends Error {}
class RetryAfterRefreshError extends Error {}

/**
 * Connects to the submission SSE stream and forwards `submission-update` events
 * to the caller.
 *
 * - Team role: receives updates only for the authenticated team's own submissions.
 * - Admin role: receives all submission updates across all teams and contests.
 *
 * Uses @microsoft/fetch-event-source with watchdog and exponential back-off,
 * matching the existing contest/clarification stream pattern.
 */
export function useSubmissionStream({
  role,
  enabled = true,
  onEvent,
  onError,
}: SubmissionStreamHandlers): { connectionState: SubmissionStreamConnectionState } {
  const [connectionState, setConnectionState] =
    useState<SubmissionStreamConnectionState>("closed");

  const handlersRef = useRef({ onEvent, onError });
  handlersRef.current = { onEvent, onError };

  useEffect(() => {
    if (!enabled) {
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
        await fetchEventSource(streamUrl(endpointFor(role)), {
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
            if (event.event !== "submission-update") return;

            try {
              const parsed = JSON.parse(event.data);
              if (!isSubmissionStreamEvent(parsed)) return;
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
  }, [enabled, role]);

  return { connectionState };
}
