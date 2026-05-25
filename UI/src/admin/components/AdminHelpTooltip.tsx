import { Info } from "lucide-react";
import { ReactNode } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "./ui/tooltip";

interface AdminHelpTooltipProps {
  content: ReactNode;
  label?: string;
  className?: string;
}

export function AdminHelpTooltip({
  content,
  label = "More information",
  className = "",
}: AdminHelpTooltipProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={label}
          className={`inline-flex h-6 w-6 items-center justify-center rounded-md text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 ${className}`}
        >
          <Info className="h-4 w-4" aria-hidden="true" />
        </button>
      </TooltipTrigger>
      <TooltipContent side="top" sideOffset={6} className="max-w-xs bg-slate-950 text-slate-50">
        <div className="text-xs leading-5">{content}</div>
      </TooltipContent>
    </Tooltip>
  );
}
