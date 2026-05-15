import * as React from "react";

import { cn } from "./utils";

function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "flex h-9 w-full min-w-0 rounded-md border border-outline-variant/20 bg-surface-container-lowest px-3 py-1 text-base text-on-surface placeholder:text-on-surface-soft selection:bg-primary/30 selection:text-on-surface outline-none transition-[color,border-color,box-shadow] file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-on-surface disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-55 md:text-sm",
        "focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/25",
        "aria-invalid:border-error aria-invalid:ring-error/30",
        className,
      )}
      {...props}
    />
  );
}

export { Input };
