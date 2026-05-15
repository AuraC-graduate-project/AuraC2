import { render, screen } from "@testing-library/react";
import { ScoreboardView } from "./ScoreboardView";
import { scoreboardSnapshot, revealResponse } from "../../test/scoreboardFixtures";
import {
  getAdminScoreboard,
  getScoreboardRevealState,
} from "../services/api";

vi.mock("../../hooks/useScoreboardStream", () => ({
  useScoreboardStream: vi.fn(() => ({ connectionState: "open" })),
}));

vi.mock("../services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/api")>();
  return {
    ...actual,
    getAdminScoreboard: vi.fn(),
    getScoreboardRevealState: vi.fn(),
    resetScoreboardReveal: vi.fn(),
    revealAllScoreboardCells: vi.fn(),
    revealNextScoreboardCell: vi.fn(),
    startScoreboardReveal: vi.fn(),
  };
});

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}));

describe("ScoreboardView", () => {
  it("loads the admin snapshot and renders freeze/reveal controls", async () => {
    vi.mocked(getAdminScoreboard).mockResolvedValue(
      scoreboardSnapshot({
        metadata: {
          audience: "ADMIN",
          adminLive: true,
          scoreboardFrozen: true,
          effectiveState: "RUNNING",
        },
      })
    );
    vi.mocked(getScoreboardRevealState).mockResolvedValue(revealResponse());

    render(<ScoreboardView contestId={1} />);

    expect(await screen.findByText("ICPC Local")).toBeInTheDocument();
    expect(screen.getByText("Admin live")).toBeInTheDocument();
    expect(screen.getByText("Official frozen")).toBeInTheDocument();
    expect(screen.getAllByText("0/2 cells revealed")).toHaveLength(2);
    expect(screen.getByRole("button", { name: /start/i })).toBeDisabled();
  });
});
