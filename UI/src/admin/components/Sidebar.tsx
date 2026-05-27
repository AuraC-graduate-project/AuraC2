import { 
  LayoutDashboard, 
  Users, 
  FileCode, 
  Send, 
  MessageSquare, 
  Trophy,
  Medal,
  RotateCcw,
  FlaskConical,
} from 'lucide-react';
import auraSymbol from '../../assets/aura-symbol.png';

interface SidebarProps {
  activeView: string;
  setActiveView: (view: string) => void;
}

const menuItems = [
  { icon: LayoutDashboard, label: 'Overview', displayLabel: 'Overview' },
  { icon: Trophy, label: 'Contests', displayLabel: 'Contests' },
  { icon: Users, label: 'Teams', displayLabel: 'Teams' },
  { icon: FileCode, label: 'Problems', displayLabel: 'Problems' },
  { icon: Send, label: 'Submissions', displayLabel: 'Submissions' },
  { icon: MessageSquare, label: 'Clarifications', displayLabel: 'Clarifications' },
  { icon: Medal, label: 'Scoreboard', displayLabel: 'Scoreboard' },
  { icon: RotateCcw, label: 'Rejudge', displayLabel: 'Rejudge' },
  { icon: FlaskConical, label: 'RunLab', displayLabel: 'Run Lab' },
];

export function Sidebar({ activeView, setActiveView }: SidebarProps) {
  return (
    <aside className="aura-sidebar flex w-80 shrink-0 flex-col border-r border-slate-800/40 bg-[#1E3A5F] text-white">
      <div className="border-b border-white/10 p-7">
        <div className="flex items-center gap-3">
          <div className="aura-mark aura-mark-sidebar p-2.5">
            <img src={auraSymbol} alt="AuraC2 logo" className="h-full w-full object-contain" />
          </div>
          <div>
            <h2 className="text-3xl font-semibold tracking-normal">AuraC<sup className="text-base">2</sup></h2>
            <p className="mt-1 text-sm text-blue-100">Aura Contest Control</p>
          </div>
        </div>
        <div className="mt-5 rounded-md border border-white/10 bg-white/5 px-4 py-3 text-xs font-semibold uppercase tracking-wide text-blue-100">
          Administrator
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto p-5">
        <ul className="space-y-3">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeView === item.label;
            const isFuture = item.label.includes('(Future)');
            const displayLabel = item.displayLabel ?? item.label.replace(' (Future)', '');
            
            return (
              <li key={item.label}>
                <button
                  onClick={() => setActiveView(item.label)}
                  className={`aura-nav-item flex w-full items-center gap-4 rounded-md px-4 py-3.5 text-left text-base font-medium transition ${
                    isActive
                      ? 'bg-white text-[#1E3A5F] shadow-sm'
                      : 'text-blue-50 hover:bg-white/10 hover:text-white'
                  }`}
                >
                  <Icon className="h-5 w-5 shrink-0" />
                  <span className="flex-1">{displayLabel}</span>
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
    </aside>
  );
}
