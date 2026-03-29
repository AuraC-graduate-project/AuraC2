import { LogOut, User } from "lucide-react";
import { Button } from "./ui/button";
import { toast } from "sonner";

export function TopNav({ onLogout }: { onLogout: () => void }) {
  const handleLogout = async () => {
    await onLogout();
    toast.success("Logged out");
  };

  return (
    <header className="bg-white border-b px-8 py-4 flex justify-between items-center">
      <h3>Dashboard</h3>

      <div className="flex items-center gap-4">
        <User />
        <Button onClick={handleLogout}>
          <LogOut className="w-4 h-4" /> Logout
        </Button>
      </div>
    </header>
  );
}
