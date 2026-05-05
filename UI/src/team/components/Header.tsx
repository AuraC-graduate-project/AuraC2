import { useEffect, useState } from "react";
import { Clock, LogOut, UserRound } from "lucide-react";
import { StatusBadge } from "../../components/StatusBadge";
import { ThemeToggle } from "../../components/ThemeToggle";

type HeaderProps = {
  contestName?: string;
  contestStatus?: string;
  contestEndTime?: string;
  teamName: string;
  onLogout: () => void;
};

function formatTime(seconds: number) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h.toString().padStart(2, "0")}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
}

export function Header({
  contestName,
  contestStatus,
  contestEndTime,
  teamName,
  onLogout,
}: HeaderProps) {
  const [timeLeft, setTimeLeft] = useState<number | null>(null);

  useEffect(() => {
    if (!contestEndTime) {
      setTimeLeft(null);
      return;
    }

    const endMs = new Date(contestEndTime).getTime();
    if (!Number.isFinite(endMs)) {
      setTimeLeft(null);
      return;
    }

    const tick = () => {
      setTimeLeft(Math.max(0, Math.floor((endMs - Date.now()) / 1000)));
    };

    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [contestEndTime]);

  return (
    <header className="aura-topbar aura-team-header border-b border-slate-200 bg-white px-5 py-4 shadow-sm">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="flex items-center gap-4">
          <div className="aura-mark flex h-11 w-11 items-center justify-center rounded-lg bg-[#1E3A5F] text-lg font-semibold text-white">
            A
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">AuraC²</p>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-slate-950">{contestName ?? "No Active Contest"}</h1>
              {contestStatus && <StatusBadge kind="contest" value={contestStatus} />}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="inline-flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
            <Clock className="h-4 w-4 text-blue-700" />
            <span className="font-mono font-semibold">{timeLeft === null ? "--:--:--" : formatTime(timeLeft)}</span>
          </div>

          <div className="inline-flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700">
            <UserRound className="h-4 w-4 text-blue-700" />
            <span className="font-semibold">{teamName}</span>
          </div>

          <ThemeToggle />

          <button
            type="button"
            onClick={onLogout}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-blue-700 px-4 text-sm font-semibold text-white hover:bg-blue-800"
          >
            <LogOut className="h-4 w-4" />
            Logout
          </button>
        </div>
      </div>
    </header>
  );
}
