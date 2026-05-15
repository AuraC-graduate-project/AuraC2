import { render, screen } from "@testing-library/react";
import { Scoreboard } from "./Scoreboard";
import { scoreboardSnapshot } from "../../test/scoreboardFixtures";
import { getPublicScoreboard } from "../services/teamApi";

vi.mock("../../hooks/useScoreboardStream", () => ({
  useScoreboardStream: vi.fn(() => ({ connectionState: "open" })),
}));

vi.mock("../services/teamApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/teamApi")>();
  return {
    ...actual,
    getPublicScoreboard: vi.fn(),
  };
});

describe("Scoreboard", () => {
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
    expect(screen.getByText(/hidden cells will update during the reveal/i)).toBeInTheDocument();
    expect(screen.getByTitle("Contains hidden post-freeze activity")).toBeInTheDocument();
  });
});
