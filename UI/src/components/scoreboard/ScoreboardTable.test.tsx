import { render, screen } from "@testing-library/react";
import { ScoreboardTable } from "./ScoreboardTable";
import { scoreboardSnapshot } from "../../test/scoreboardFixtures";

describe("ScoreboardTable", () => {
  it("renders standings, first-to-solve, hidden cells, and changed rows", () => {
    render(<ScoreboardTable snapshot={scoreboardSnapshot()} changedTeamIds={[100]} />);

    expect(screen.getByText("alpha")).toBeInTheDocument();
    expect(screen.getByText("#100")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByTitle("Accepted at 22 min with 1 wrong attempt(s)")).toBeInTheDocument();
    expect(screen.getByTitle("Contains hidden post-freeze activity")).toBeInTheDocument();
    expect(screen.getByRole("row", { name: /1 alpha #100 1 42/i })).toHaveClass("bg-blue-50/70");
  });
});
