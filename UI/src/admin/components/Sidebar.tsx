import {
  LayoutDashboard,
  Users,
  FileCode,
  Send,
  MessageSquare,
  Trophy,
  Medal,
  RotateCcw,
  LogOut,
} from 'lucide-react';

interface SidebarProps {
  activeView: string;
  setActiveView: (view: string) => void;
  onLogout: () => void;
}

const menuItems = [
  { icon: LayoutDashboard, label: 'Overview' },
  { icon: Trophy, label: 'Contests' },
  { icon: Users, label: 'Teams' },
  { icon: FileCode, label: 'Problems' },
  { icon: Send, label: 'Submissions' },
  { icon: MessageSquare, label: 'Clarifications' },
  { icon: Medal, label: 'Scoreboard (Future)' },
  { icon: RotateCcw, label: 'Rejudge' },
];

export function Sidebar({ activeView, setActiveView, onLogout }: SidebarProps) {
  return (
    <aside className="aura-sidebar flex w-72 shrink-0 flex-col bg-surface-container-low text-on-surface">
      <div className="px-6 pt-7 pb-6">
        <div className="flex items-center gap-3">
          <div className="aura-mark aura-mark-sidebar">A</div>
          <div>
            <h2 className="font-display text-2xl font-semibold tracking-tight text-on-surface">AuraC²</h2>
            <p className="mt-0.5 text-xs uppercase tracking-[0.18em] text-on-surface-soft">Contest Control</p>
          </div>
        </div>
        <div className="mt-5 inline-flex items-center gap-2 rounded-full bg-primary-fixed px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-primary dark:bg-primary/15">
          Administrator
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-4">
        <ul className="space-y-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeView === item.label;
            const isFuture = item.label.includes('(Future)');

            return (
              <li key={item.label}>
                <button
                  type="button"
                  onClick={() => setActiveView(item.label)}
                  aria-current={isActive ? 'true' : undefined}
                  className={`aura-nav-item flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium ${
                    isActive
                      ? 'bg-surface-container-highest text-on-surface'
                      : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span className="flex-1">{item.label.replace(' (Future)', '')}</span>
                  {isFuture && (
                    <span className="rounded-full bg-secondary-container px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-on-secondary-container">
                      Soon
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="px-4 pb-5 pt-3">
        <button
          type="button"
          onClick={onLogout}
          className="aura-nav-item mb-3 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-on-surface-variant hover:bg-surface-container hover:text-on-surface"
        >
          <LogOut className="h-4 w-4" />
          Logout
        </button>
        <p className="text-xs leading-5 text-on-surface-soft">
          University contest operations for administrators and contest managers.
        </p>
      </div>
    </aside>
  );
}
