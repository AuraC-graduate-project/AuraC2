import { 
  LayoutDashboard, 
  Users, 
  FileCode, 
  Send, 
  MessageSquare, 
  Shield 
} from 'lucide-react';

interface SidebarProps {
  activeView: string;
  setActiveView: (view: string) => void;
}

const menuItems = [
  { icon: LayoutDashboard, label: 'Overview' },
  { icon: Users, label: 'Teams' },
  { icon: FileCode, label: 'Problems' },
  { icon: Send, label: 'Submissions' },
  { icon: MessageSquare, label: 'Clarifications' },
  { icon: Shield, label: 'Security Monitor' },
];

export function Sidebar({ activeView, setActiveView }: SidebarProps) {
  return (
    <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
      {/* Logo/Brand */}
      <div className="p-6 border-b border-gray-200">
        <h2 className="text-slate-800">AuraC² Admin</h2>
      </div>

      {/* Navigation Menu */}
      <nav className="flex-1 p-4">
        <ul className="space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeView === item.label;
            
            return (
              <li key={item.label}>
                <button
                  onClick={() => setActiveView(item.label)}
                  className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                    isActive
                      ? 'bg-blue-50 text-blue-600'
                      : 'text-slate-600 hover:bg-gray-50 hover:text-slate-900'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                  <span>{item.label}</span>
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
    </aside>
  );
}
