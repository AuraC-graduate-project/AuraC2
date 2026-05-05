import { Moon, Sun } from "lucide-react";
import { useTheme } from "./ThemeProvider";

export function ThemeToggle({ className = "" }: { className?: string }) {
  const { isDark, toggleTheme } = useTheme();

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
      title={isDark ? "Switch to light mode" : "Switch to dark mode"}
      className={`aura-theme-toggle inline-flex h-10 items-center gap-2 rounded-md border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-[#2d3746] dark:bg-[#151c26] dark:text-[#d7dde7] dark:hover:bg-[#1b2430] ${className}`}
    >
      {isDark ? <Sun className="h-4 w-4 text-[#d1c09a]" /> : <Moon className="h-4 w-4 text-blue-700" />}
      <span>{isDark ? "Light" : "Dark"}</span>
    </button>
  );
}
