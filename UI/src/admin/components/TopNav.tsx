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
    <header className="aura-topbar flex items-center justify-between border-b border-slate-200 bg-white px-8 py-4">
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Aura Contest Control</p>
        <h1 className="text-xl font-semibold text-slate-950">{activeView}</h1>
      </div>

      <div className="flex items-center gap-4">
        <div className="hidden items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 md:flex">
          <User className="h-4 w-4 text-blue-700" />
          Admin
        </div>
        <Button variant="outline" className="gap-2" onClick={() => window.location.reload()}>
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
        <ThemeToggle />
        <Button onClick={handleLogout} className="gap-2 bg-blue-700 hover:bg-blue-800">
          <LogOut className="h-4 w-4" /> Logout
        </Button>
      </div>
    </header>
  );
}
