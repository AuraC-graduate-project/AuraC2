import { 
  LayoutDashboard, 
  Users, 
  FileCode, 
  Send, 
  MessageSquare, 
  Trophy,
  Medal,
  RotateCcw,
} from 'lucide-react';

interface SidebarProps {
  activeView: string;
  setActiveView: (view: string) => void;
}

const menuItems = [
  { icon: LayoutDashboard, label: 'Overview' },
  { icon: Trophy, label: 'Contests' },
  { icon: Users, label: 'Teams' },
  { icon: FileCode, label: 'Problems' },
  { icon: Send, label: 'Submissions' },
  { icon: MessageSquare, label: 'Clarifications' },
  { icon: Medal, label: 'Scoreboard' },
  { icon: RotateCcw, label: 'Rejudge' },
];

export function Sidebar({ activeView, setActiveView }: SidebarProps) {
  return (
    <aside className="aura-sidebar flex w-72 shrink-0 flex-col border-r border-slate-800/40 bg-[#1E3A5F] text-white">
      <div className="border-b border-white/10 p-6">
        <div className="flex items-center gap-3">
          <div className="aura-mark aura-mark-sidebar">A</div>
          <div>
            <h2 className="text-2xl font-semibold tracking-normal">AuraC²</h2>
            <p className="mt-1 text-sm text-blue-100">Aura Contest Control</p>
          </div>
        </div>
        <div className="mt-4 rounded-md border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-blue-100">
          Administrator
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto p-4">
        <ul className="space-y-1.5">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeView === item.label;
            const isFuture = item.label.includes('(Future)');
            
            return (
              <li key={item.label}>
                <button
                  onClick={() => setActiveView(item.label)}
                  className={`aura-nav-item flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-sm font-medium transition ${
                    isActive
                      ? 'bg-white text-[#1E3A5F] shadow-sm'
                      : 'text-blue-50 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span className="flex-1">{item.label.replace(' (Future)', '')}</span>
                  {isFuture && (
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                      isActive ? 'bg-amber-100 text-amber-800' : 'bg-white/10 text-amber-100'
                    }`}>
                      Future
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="border-t border-white/10 p-4">
        <p className="text-xs leading-5 text-blue-100">
          University contest operations for administrators and contest managers.
        </p>
      </div>
    </aside>
  );
}
