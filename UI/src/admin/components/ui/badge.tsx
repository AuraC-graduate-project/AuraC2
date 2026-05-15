import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "./utils";

const badgeVariants = cva(
  "inline-flex items-center justify-center rounded-full px-2.5 py-0.5 text-xs font-medium tracking-tight w-fit whitespace-nowrap shrink-0 [&>svg]:size-3 gap-1 [&>svg]:pointer-events-none focus-visible:ring-2 focus-visible:ring-primary/30 aria-invalid:ring-error/30 aria-invalid:border-error transition-colors overflow-hidden",
  {
    variants: {
      variant: {
        default:
          "bg-primary-fixed text-primary [a&]:hover:bg-primary-fixed/80 dark:bg-primary/15 dark:text-primary",
        secondary:
          "bg-secondary-container text-on-secondary-container [a&]:hover:bg-secondary-container/80",
        destructive:
          "bg-error-container text-on-error-container [a&]:hover:bg-error-container/85",
        outline:
          "border border-outline-variant/25 bg-transparent text-on-surface [a&]:hover:bg-surface-container-low",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  },
);

function Badge({
  className,
  variant,
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : "span";

  return (
    <Comp
      data-slot="badge"
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  );
}

export { Badge, badgeVariants };
