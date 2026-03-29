import React, { useEffect, useState } from "react";
import { Trophy, Clock, LogOut } from "lucide-react";

type HeaderProps = {
  contestName?: string;
  contestEndTime?: string;   // ✅ ADD THIS
  teamName: string;
  onLogout: () => void;
};
export function Header({
  contestName,
  contestEndTime,   // ✅ YOU FORGOT THIS
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
      console.error("Invalid contestEndTime:", contestEndTime);
      setTimeLeft(null);
      return;
    }

    const tick = () => {
      const diff = Math.max(0, Math.floor((endMs - Date.now()) / 1000));
      setTimeLeft(diff);
    };

    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [contestEndTime]);

  const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h.toString().padStart(2, "0")}:${m
      .toString()
      .padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
  };

  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4 shadow-sm">
      <div className="flex items-center justify-between">

        {/* Contest */}
        <div className="flex items-center gap-2">
          <Trophy className="w-6 h-6 text-[#FACC15]" />
          <h1 className="text-gray-900">
            {contestName ?? "No Active Contest"}
          </h1>
        </div>

        {/* Timer */}
        <div className="flex items-center gap-2 px-4 py-2 bg-gray-50 rounded-lg border border-gray-200">
          <Clock className="w-5 h-5 text-gray-600" />
          <span className="text-gray-900">
            {timeLeft === null ? "--:--:--" : formatTime(timeLeft)}
          </span>
        </div>

        {/* Team + Logout */}
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <span className="text-gray-600">Team:</span>
            <span className="px-3 py-1 bg-[#FACC15] bg-opacity-20 text-gray-900 rounded-md border border-[#FACC15]">
              {teamName}
            </span>
          </div>

          <button
            onClick={onLogout}
            className="flex items-center gap-2 px-3 py-2 border border-gray-200 rounded-md hover:bg-gray-50"
          >
            <LogOut className="w-4 h-4 text-gray-600" />
            <span className="text-gray-600">Logout</span>
          </button>
        </div>

      </div>
    </header>
  );
}
