import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-bold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "border-transparent bg-[var(--ui-button-primary-bg)] text-[var(--ui-button-primary-fg)]",
        secondary: "border-[var(--ui-border)] bg-[var(--ui-muted)] text-[var(--ui-text-soft)]",
        destructive: "border-transparent bg-destructive text-destructive-foreground",
        outline: "text-[var(--ui-text)]",
        success: "border-[var(--ui-border-accent)] bg-[var(--ui-accent)] text-[var(--ui-accent-strong)]",
        warning: "border-[var(--ui-border)] bg-[var(--ui-muted)] text-[var(--ui-text-soft)]",
        purple: "border-[var(--ui-border-accent)] bg-[var(--ui-accent)] text-[var(--ui-accent-strong)]",
        blue: "border-[var(--ui-border)] bg-[var(--ui-muted)] text-[var(--ui-text-soft)]",
        slate: "border-[var(--ui-border)] bg-[var(--ui-muted)] text-[var(--ui-text-muted)]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
