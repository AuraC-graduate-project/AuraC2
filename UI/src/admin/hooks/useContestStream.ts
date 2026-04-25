import { useEffect, useRef, useState } from "react";
import type {
  ContestStreamSnapshot,
  ContestStreamUpdate,
} from "../types/api";

export type ContestStreamConnectionState = "connecting" | "open" | "closed";

/**
 * So what does useContestStream do in one sentence?
 *
 *  It is a custom React hook that:
 *
 *    opens an SSE connection to the backend
 *    listens for snapshot and contest-update
 *    parses incoming JSON
 *    forwards data to callbacks
 *    tracks connection status for the UI
 */

// This says the hook accepts an object like { onSnapshot: (snapshot) => void, onContestUpdate: (update) => void }
// onSnapshot      → called when backend sends the full current state
// onContestUpdate → called when backend sends one lifecycle update
type ContestStreamHandlers = {
  onSnapshot?: (snapshot: ContestStreamSnapshot) => void;
  onContestUpdate?: (update: ContestStreamUpdate) => void;
};

const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";

function streamUrl(): string {
  const path = "/api/contest/stream";
  return API_BASE_URL ? `${API_BASE_URL}${path}` : path;
}

/**
 * This hook receives callbacks and returns connection state.
 * The main logic is in the useEffect that opens the SSE connection and sets up event listeners.
 * The handlers are stored in a ref to ensure the latest versions are called without needing to re-subscribe to the SSE stream.
 * The connection state is managed with useState and updated based on the EventSource's lifecycle events.
 * It runs only once on mount and cleans up on unmount to ensure efficient resource management.
 *
 * @param handlers - An object containing optional callbacks for handling snapshot and contest update events from the SSE stream.
 * @returns An object containing the current connection state of the SSE stream, which can be "connecting", "open", or "closed".
 */
export function useContestStream(
  handlers: ContestStreamHandlers
): { connectionState: ContestStreamConnectionState } {
  const handlersRef = useRef<ContestStreamHandlers>(handlers);
  //// Keep latest callbacks without recreating EventSource.
  handlersRef.current = handlers;

  const [connectionState, setConnectionState] =
    useState<ContestStreamConnectionState>("connecting");

  // This is where the SSE connection is opened.
  useEffect(() => {// Main effect: open SSE once
    if (typeof EventSource === "undefined") {
      setConnectionState("closed");
      return;
    }

    const source = new EventSource(streamUrl()); // This opens the live SSE stream. At this moment the browser starts listening to the backend.

    /** Why a snapshot? The snapshot event provides the full current state of all contests immediately upon connection.
     *  This ensures that the client has an up-to-date view right away,
     *  without waiting for the next incremental update.
     *  After receiving the snapshot, the client can then apply subsequent contest-update events to stay in sync with any changes.
     **/
    const handleSnapshot = (event: MessageEvent) => {//when server sends a snapshot event, this callback is called with the event data. We parse the JSON payload and call the onSnapshot handler if provided.
      try {
        const data = JSON.parse(event.data) as ContestStreamSnapshot;
        handlersRef.current.onSnapshot?.(data);// it calls applySnapshot inside parent .
      } catch (err) {
        console.warn("Failed to parse SSE snapshot payload", err);
      }
    };

    /** What is this function?
     * The handleUpdate function is a callback that gets called whenever
     * the server sends a "contest-update" event through the SSE stream.
     * It receives the event data, attempts to parse it as JSON,
     * and then calls the onContestUpdate handler (if provided) with the parsed data.
     * This allows the client to react to incremental updates about contest changes in real time.
     * So if contest changes from UPCOMING → RUNNING, backend can push a targeted update.
    **/
    const handleUpdate = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data) as ContestStreamUpdate;
        handlersRef.current.onContestUpdate?.(data);
      } catch (err) {
        console.warn("Failed to parse SSE contest-update payload", err);
      }
    };

    /** What are these?
     *  We register event listeners for both
     *  "snapshot" and "contest-update" events on the EventSource.
     *  This allows us to handle the initial state of contests
     *  as well as any subsequent updates sent by the server.
     *  The handlers are typecast to EventListener to satisfy TypeScript's type checking,
     *  since our callbacks have specific signatures that differ from the default EventListener type.
     *
      So backend probably sends things like:
          event: snapshot
          data: {...}
      and
          event: contest-update
          data: {...}
     **/
    source.addEventListener("snapshot", handleSnapshot as EventListener);
    source.addEventListener("contest-update", handleUpdate as EventListener);

    /**When connection succeeds, UI becomes live.
     *  If connection fails, we check if the browser has given up (readyState === CLOSED)
     *  or is still trying (readyState === CONNECTING) and update the connection state accordingly.
     *  The browser will automatically attempt to reconnect with an exponential backoff strategy,
     *  so we don't need to implement that logic ourselves.
     **/
     source.onopen = () => setConnectionState("open");
     source.onerror = () => {
      // Browser retries automatically while readyState stays CONNECTING.
      setConnectionState(
        source.readyState === EventSource.CLOSED ? "closed" : "connecting"
      );
    };

    /**This runs when the component unmounts.
     *  We clean up by removing the event listeners and closing
     *  the EventSource connection to prevent memory leaks and unnecessary network activity.
    **/
     return () => {
      source.removeEventListener("snapshot", handleSnapshot as EventListener);
      source.removeEventListener(
        "contest-update",
        handleUpdate as EventListener
      );
      source.close();
    };
  }, []);// [] means this effect runs only once when the component mounts and cleans up when it unmounts.

  return { connectionState };
}
