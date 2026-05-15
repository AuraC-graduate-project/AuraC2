import { renderHook, waitFor } from "@testing-library/react";
import { fetchEventSource } from "@microsoft/fetch-event-source";
import { useScoreboardStream } from "./useScoreboardStream";
import { scoreboardPayload, scoreboardSnapshot } from "../test/scoreboardFixtures";

vi.mock("@microsoft/fetch-event-source", () => ({
  fetchEventSource: vi.fn(),
}));

vi.mock("../admin/services/api", () => ({
  ensureRefreshedOnce: vi.fn(),
  getAccessToken: vi.fn(() => "access-token"),
}));

const mockedFetchEventSource = vi.mocked(fetchEventSource);

describe("useScoreboardStream", () => {
  beforeEach(() => {
    mockedFetchEventSource.mockReset();
  });

  it("opens the role-specific stream and dispatches snapshot and row update events", async () => {
    const snapshot = scoreboardSnapshot();
    const payload = scoreboardPayload();
    const onSnapshot = vi.fn();
    const onUpdate = vi.fn();

    mockedFetchEventSource.mockImplementation(async (_url, options) => {
      await options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { "content-type": "text/event-stream" },
        })
      );
      options.onmessage?.({ event: "snapshot", data: JSON.stringify(snapshot), id: "", retry: 0 });
      options.onmessage?.({ event: "scoreboard-update", data: JSON.stringify(payload), id: "", retry: 0 });
    });

    renderHook(() =>
      useScoreboardStream({
        contestId: 1,
        role: "ADMIN",
        onSnapshot,
        onUpdate,
      })
    );

    await waitFor(() => expect(onSnapshot).toHaveBeenCalledWith(snapshot));
    expect(onUpdate).toHaveBeenCalledWith(payload);
    expect(mockedFetchEventSource).toHaveBeenCalledWith(
      "/api/admin/scoreboard/contests/1/stream",
      expect.objectContaining({
        headers: { Authorization: "Bearer access-token" },
      })
    );
  });

  it("notifies the caller when an SSE version gap is detected", async () => {
    const onVersionGap = vi.fn();

    mockedFetchEventSource.mockImplementation(async (_url, options) => {
      await options.onopen?.(
        new Response(null, {
          status: 200,
          headers: { "content-type": "text/event-stream" },
        })
      );
      options.onmessage?.({
        event: "scoreboard-update",
        data: JSON.stringify(scoreboardPayload({ version: 4, previousVersion: 1 })),
        id: "",
        retry: 0,
      });
    });

    renderHook(() =>
      useScoreboardStream({
        contestId: 1,
        role: "PUBLIC",
        snapshotVersion: 1,
        onVersionGap,
      })
    );

    await waitFor(() => expect(onVersionGap).toHaveBeenCalled());
    expect(mockedFetchEventSource).toHaveBeenCalledWith(
      "/api/scoreboard/contests/1/stream",
      expect.any(Object)
    );
  });
});
