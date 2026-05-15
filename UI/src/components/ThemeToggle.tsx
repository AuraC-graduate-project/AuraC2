import { Moon, Sun } from "lucide-react";
import { useTheme } from "./ThemeProvider";

export function ThemeToggle({ className = "" }: { className?: string }) {
  const { isDark, toggleTheme } = useTheme();
  const nextLabel = isDark ? "Manuscript" : "Obsidian";

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? "Switch to Digital Manuscript (light)" : "Switch to Obsidian Compiler (dark)"}
      title={isDark ? "Switch to Digital Manuscript (light)" : "Switch to Obsidian Compiler (dark)"}
      className={`aura-theme-toggle inline-flex h-9 items-center gap-2 rounded-xl bg-surface-container-high px-3 text-sm font-medium text-on-surface-variant transition hover:bg-surface-container-highest hover:text-on-surface dark:bg-surface-container dark:hover:bg-surface-container-high ${className}`}
    >
      {isDark ? <Sun className="h-4 w-4 text-primary" /> : <Moon className="h-4 w-4 text-primary" />}
      <span>{nextLabel}</span>
    </button>
  );
}
