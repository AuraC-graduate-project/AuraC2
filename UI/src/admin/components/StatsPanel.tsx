import { Card, CardContent } from './ui/card';
import { Users, FileCode, Send, MessageSquare } from 'lucide-react';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from './ui/tooltip';

const stats = [
  {
    label: 'Total Teams',
    value: '—',
    icon: Users,
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
  },
  {
    label: 'Problems',
    value: '—',
    icon: FileCode,
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
  },
  {
    label: 'Submissions',
    value: '—',
    icon: Send,
    color: 'text-green-600',
    bgColor: 'bg-green-50',
  },
  {
    label: 'Clarifications Pending',
    value: '—',
    icon: MessageSquare,
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
  },
];

export function StatsPanel() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {stats.map((stat) => {
        const Icon = stat.icon;
        
        return (
          <TooltipProvider key={stat.label}>
            <Tooltip>
              <TooltipTrigger asChild>
                <Card className="border border-gray-200 shadow-sm opacity-60 cursor-help">
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between">
                      <div>
                        <p className="text-slate-600 mb-2">{stat.label}</p>
                        <p className="text-slate-900">{stat.value}</p>
                      </div>
                      <div className={`${stat.bgColor} ${stat.color} p-3 rounded-lg`}>
                        <Icon className="w-6 h-6" />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TooltipTrigger>
              <TooltipContent>
                <p>No endpoint yet</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        );
      })}
    </div>
  );
}
