import {
  getActiveContest,
  getEndedContests,
  getPausedContest,
  getUpcomingContest,
} from "../services/api";
import type { ContestResponse } from "../types/api";

export type ContestBucket = "Active" | "Upcoming" | "Paused" | "Ended";
export type ContestOption = ContestResponse & { bucket: ContestBucket };

export function contestLabel(contest: ContestOption): string {
  return `${contest.title || "Untitled contest"} (#${contest.id})`;
}

export function sortEndedContests(contests: ContestResponse[]): ContestResponse[] {
  return [...contests].sort((a, b) => {
    const aTime = Date.parse(a.startTime);
    const bTime = Date.parse(b.startTime);
    return bTime - aTime;
  });
}

export async function loadContestOptions(): Promise<ContestOption[]> {
  const next: ContestOption[] = [];
  const seen = new Set<number>();

  const pushContest = (
    contest: ContestResponse | null | undefined,
    bucket: ContestBucket
  ) => {
    if (!contest || seen.has(contest.id)) return;
    seen.add(contest.id);
    next.push({ ...contest, bucket });
  };

  const [active, upcoming, paused, ended] = await Promise.allSettled([
    getActiveContest(),
    getUpcomingContest(),
    getPausedContest(),
    getEndedContests(),
  ]);

  if (active.status === "fulfilled") pushContest(active.value, "Active");
  if (upcoming.status === "fulfilled") pushContest(upcoming.value, "Upcoming");
  if (paused.status === "fulfilled") pushContest(paused.value, "Paused");
  if (ended.status === "fulfilled") {
    sortEndedContests(ended.value).forEach((contest) =>
      pushContest(contest, "Ended")
    );
  }

  return next;
}
