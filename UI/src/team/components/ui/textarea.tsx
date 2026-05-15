import * as React from "react";

import { cn } from "./utils";

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(
        "flex field-sizing-content min-h-16 w-full resize-none rounded-md border border-outline-variant/20 bg-surface-container-lowest px-3 py-2 text-base text-on-surface placeholder:text-on-surface-soft outline-none transition-[color,border-color,box-shadow]",
        "focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/25",
        "aria-invalid:border-error aria-invalid:ring-error/30",
        "disabled:cursor-not-allowed disabled:opacity-55 md:text-sm",
        className,
      )}
      {...props}
    />
  );
}

export { Textarea };
