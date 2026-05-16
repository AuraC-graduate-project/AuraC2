import "../../test/setup";
import { act, render, screen, waitFor } from "@testing-library/react";
import { Scoreboard } from "./Scoreboard";
import { scoreboardPayload, scoreboardRow, scoreboardSnapshot } from "../../test/scoreboardFixtures";
import { getPublicScoreboard } from "../services/teamApi";

type ScoreboardStreamHandlerFixture = {
  onRevealStep?: (payload: ReturnType<typeof scoreboardPayload>) => void;
};

const scoreboardStreamHandlers = vi.hoisted(() => [] as ScoreboardStreamHandlerFixture[]);

vi.mock("../../hooks/useScoreboardStream", () => ({
  useScoreboardStream: vi.fn((handlers: ScoreboardStreamHandlerFixture) => {
    scoreboardStreamHandlers.push(handlers);
    return { connectionState: "open" };
  }),
}));

vi.mock("../../hooks/useSubmissionStream", () => ({
  useSubmissionStream: vi.fn(() => ({ connectionState: "open" })),
}));

vi.mock("../services/teamApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/teamApi")>();
  return {
    ...actual,
    getPublicScoreboard: vi.fn(),
  };
});

describe("Scoreboard", () => {
  beforeEach(() => {
    scoreboardStreamHandlers.length = 0;
    vi.clearAllMocks();
  });

  it("loads the public snapshot and shows the freeze indicator", async () => {
    vi.mocked(getPublicScoreboard).mockResolvedValue(
      scoreboardSnapshot({
        metadata: {
          scoreboardFrozen: true,
          revealStatus: "IN_PROGRESS",
          totalHiddenCells: 1,
        },
      })
    );

    render(<Scoreboard contestId={1} />);

    expect(await screen.findByText("alpha")).toBeInTheDocument();
    expect(screen.getByText("Frozen")).toBeInTheDocument();
    expect(screen.getByText("Official scoreboard frozen")).toBeInTheDocument();
    expect(screen.getByTitle("Contains hidden post-freeze activity")).toBeInTheDocument();
  });

  it("updates ranking when a reveal step payload arrives", async () => {
    const alphaBefore = scoreboardRow({
      rank: 1,
      teamId: 100,
      teamName: "alpha",
      solvedCount: 1,
      totalPenalty: 95,
    });
    const betaBefore = scoreboardRow({
      rank: 2,
      teamId: 200,
      teamName: "beta",
      solvedCount: 0,
      totalPenalty: 0,
      problemCells: [
        {
          problemId: 10,
          label: "A",
          solved: false,
          attempts: 0,
          wrongAttempts: 0,
          solvedTimeMinutes: null,
          penalty: null,
          firstToSolve: false,
          hidden: false,
          revealed: false,
        },
        {
          problemId: 20,
          label: "B",
          solved: false,
          attempts: 0,
          wrongAttempts: 0,
          solvedTimeMinutes: null,
          penalty: null,
          firstToSolve: false,
          hidden: true,
          revealed: false,
        },
      ],
    });

    const alphaAfter = { ...alphaBefore, rank: 2 };
    const betaAfter = scoreboardRow({
      ...betaBefore,
      rank: 1,
      solvedCount: 1,
      totalPenalty: 61,
      problemCells: [
        betaBefore.problemCells[0],
        {
          ...betaBefore.problemCells[1],
          solved: true,
          attempts: 1,
          solvedTimeMinutes: 61,
          penalty: 61,
          firstToSolve: true,
          revealed: true,
        },
      ],
    });

    vi.mocked(getPublicScoreboard).mockResolvedValue(
      scoreboardSnapshot({
        metadata: {
          scoreboardFrozen: true,
          revealStatus: "IN_PROGRESS",
          totalHiddenCells: 1,
        },
        rows: [alphaBefore, betaBefore],
      })
    );

    render(<Scoreboard contestId={1} />);

    expect(await screen.findByText("alpha")).toBeInTheDocument();

    act(() => {
      scoreboardStreamHandlers.at(-1)?.onRevealStep?.(
        scoreboardPayload({
          eventType: "scoreboard-reveal-step",
          changedTeamIds: [200, 100],
          changedRows: [betaAfter, alphaAfter],
          metadata: {
            scoreboardFrozen: true,
            revealStatus: "IN_PROGRESS",
            revealedCells: 1,
            totalHiddenCells: 1,
          },
        })
      );
    });

    await waitFor(() => expect(screen.getByLabelText("Rank up")).toBeInTheDocument());
    expect(screen.getByLabelText("Rank down")).toBeInTheDocument();
    expect(screen.getByTitle("Accepted at 61 min with 0 wrong attempt(s)")).toHaveClass(
      "aura-scoreboard-cell-revealed"
    );
  });
});
