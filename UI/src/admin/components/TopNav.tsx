import { LogOut, RefreshCw, User } from "lucide-react";
import { Button } from "./ui/button";
import { toast } from "sonner";
import { ThemeToggle } from "../../components/ThemeToggle";

export function TopNav({
  activeView,
  onLogout,
}: {
  activeView: string;
  onLogout: () => void;
}) {
  const handleLogout = async () => {
    await onLogout();
    toast.success("Logged out");
  };

  return (
    <header className="aura-topbar flex items-center justify-between px-8 py-4">
      <div>
        <p className="text-xs font-medium uppercase tracking-[0.18em] text-on-surface-soft">Aura Contest Control</p>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-on-surface">{activeView}</h1>
      </div>

      <div className="flex items-center gap-3">
        <div className="hidden items-center gap-2 rounded-xl bg-surface-container-high px-3 py-2 text-sm text-on-surface-variant md:flex">
          <User className="h-4 w-4 text-primary" />
          Admin
        </div>
        <Button variant="ghost" className="gap-2" onClick={() => window.location.reload()}>
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
        <ThemeToggle />
        <Button onClick={handleLogout} className="gap-2">
          <LogOut className="h-4 w-4" /> Logout
        </Button>
      </div>
    </header>
  );
}
