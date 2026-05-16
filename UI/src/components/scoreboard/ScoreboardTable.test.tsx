import "../../test/setup";
import { render, screen } from "@testing-library/react";
import { ScoreboardTable } from "./ScoreboardTable";
import { scoreboardRow, scoreboardSnapshot } from "../../test/scoreboardFixtures";

describe("ScoreboardTable", () => {
  it("renders standings, first-to-solve, hidden cells, and changed rows", () => {
    render(<ScoreboardTable snapshot={scoreboardSnapshot()} changedTeamIds={[100]} />);

    expect(screen.getByText("alpha")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByTitle("Accepted at 22 min with 1 wrong attempt(s)")).toBeInTheDocument();
    expect(screen.getByTitle("Contains hidden post-freeze activity")).toBeInTheDocument();
    expect(screen.getByText("Hidden")).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /1 alpha 22\+1 hidden 1 42/i })).toHaveClass("bg-sky-50/80");
  });

  it("shows reveal highlights and rank direction indicators", () => {
    render(
      <ScoreboardTable
        snapshot={scoreboardSnapshot({
          rows: [
            scoreboardRow({
              rank: 1,
              teamId: 200,
              teamName: "beta",
              solvedCount: 1,
              totalPenalty: 61,
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
                  solved: true,
                  attempts: 1,
                  wrongAttempts: 0,
                  solvedTimeMinutes: 61,
                  penalty: 61,
                  firstToSolve: true,
                  hidden: true,
                  revealed: true,
                },
              ],
            }),
            scoreboardRow({
              rank: 2,
              teamId: 100,
              teamName: "alpha",
            }),
          ],
        })}
        changedTeamIds={[200]}
        rankChanges={{ 200: "up", 100: "down" }}
      />
    );

    expect(screen.getByLabelText("Rank up")).toBeInTheDocument();
    expect(screen.getByLabelText("Rank down")).toBeInTheDocument();
    expect(screen.getByTitle("Accepted at 61 min with 0 wrong attempt(s)")).toHaveClass(
      "aura-scoreboard-cell-revealed"
    );
  });
});
