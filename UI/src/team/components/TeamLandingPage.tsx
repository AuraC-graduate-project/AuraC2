import { useEffect, useState } from "react";
import { LogOut } from "lucide-react";
import { Button } from "./ui/button";
import { ContestResponse } from "../../admin/types/api";

export type LandingLifecycle = "UPCOMING" | "PAUSED" | "ENDED" | "NONE";

type Props = {
  lifecycle: LandingLifecycle;
  contest: ContestResponse | null;
  onLogout: () => void;
};

const STATUS_LABEL: Record<LandingLifecycle, string> = {
  UPCOMING: "Before the contest",
  PAUSED: "Contest is paused",
  ENDED: "Contest has ended",
  NONE: "Check back later for upcoming contests.",
};

function formatHMS(ms: number): string {
  const t = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(t / 3600);
  const m = Math.floor((t % 3600) / 60);
  const s = t % 60;
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}

export default function TeamLandingPage({ lifecycle, contest, onLogout }: Props) {
  const [remainingMs, setRemainingMs] = useState<number | null>(null);

  useEffect(() => {
    if (lifecycle === "UPCOMING" && contest?.startTime) {
      const startMs = new Date(contest.startTime).getTime();
      if (!Number.isFinite(startMs)) {
        setRemainingMs(null);
        return;
      }
      const tick = () => setRemainingMs(Math.max(0, startMs - Date.now()));
      tick();
      const id = setInterval(tick, 1000);
      return () => clearInterval(id);
    }

    if (lifecycle === "PAUSED" && contest) {
      // Frozen value — no interval.
      setRemainingMs(contest.remainingMillis ?? 0);
      return;
    }

    setRemainingMs(null);
  }, [lifecycle, contest?.id, contest?.startTime, contest?.remainingMillis]);

  const showCountdown =
    (lifecycle === "UPCOMING" || lifecycle === "PAUSED") && remainingMs != null;

  const heading =
    lifecycle === "NONE" || !contest ? "No active contest" : contest.title;

  return (
    <div className="relative min-h-screen bg-background flex flex-col items-center justify-center p-6">
      <Button
        variant="ghost"
        onClick={onLogout}
        className="absolute top-4 right-4"
      >
        <LogOut className="w-4 h-4 mr-2" />
        Logout
      </Button>

      <div className="text-center space-y-8 max-w-2xl">
        <p className="text-xs font-medium uppercase tracking-[0.22em] text-primary">{STATUS_LABEL[lifecycle]}</p>

        <h1 className="font-display text-4xl md:text-5xl font-semibold tracking-tight text-on-surface">
          {heading}
        </h1>

        {showCountdown && (
          <div className="font-mono text-5xl md:text-6xl text-on-surface tabular-nums tracking-tight">
            {formatHMS(remainingMs!)}
          </div>
        )}
      </div>
    </div>
  );
}
