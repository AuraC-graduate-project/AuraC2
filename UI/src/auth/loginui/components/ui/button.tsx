import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "./utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-medium tracking-tight transition-all disabled:pointer-events-none disabled:opacity-55 [&_svg]:pointer-events-none [&_svg:not([class*='size-'])]:size-4 shrink-0 [&_svg]:shrink-0 outline-none focus-visible:ring-2 focus-visible:ring-primary/30 focus-visible:ring-offset-0 aria-invalid:ring-error/30 aria-invalid:border-error",
  {
    variants: {
      variant: {
        default:
          "bg-primary text-on-primary shadow-[0_1px_0_rgba(255,255,255,0.06)_inset] hover:bg-primary-container dark:bg-[image:var(--primary-gradient)] dark:hover:brightness-110",
        destructive:
          "bg-error/12 text-error hover:bg-error/20 dark:bg-error/15 dark:text-error focus-visible:ring-error/30",
        success:
          "bg-tertiary-container text-on-tertiary-container hover:brightness-95 dark:bg-tertiary/15 dark:text-tertiary",
        warning:
          "bg-secondary-container text-on-secondary-container hover:brightness-95 dark:bg-secondary/15 dark:text-secondary",
        info:
          "bg-primary-fixed text-primary hover:brightness-95 dark:bg-primary/15 dark:text-primary",
        outline:
          "border border-outline-variant/20 bg-transparent text-on-surface hover:bg-surface-container-low hover:border-outline-variant/35 dark:hover:bg-surface-bright",
        secondary:
          "bg-surface-container-high text-on-surface hover:bg-surface-container-highest dark:bg-surface-container dark:hover:bg-surface-container-high",
        ghost:
          "bg-transparent text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface dark:hover:bg-surface-bright",
        link:
          "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-4 py-2 has-[>svg]:px-3",
        sm: "h-8 rounded-lg gap-1.5 px-3 has-[>svg]:px-2.5",
        lg: "h-10 rounded-xl px-6 has-[>svg]:px-4",
        icon: "size-9 rounded-xl",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  }) {
  const Comp = asChild ? Slot : "button";

  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  );
}

export { Button, buttonVariants };
