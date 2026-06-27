import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-full text-sm font-bold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 active:scale-[0.98]",
  {
    variants: {
      variant: {
        default: "bg-[var(--ui-button-primary-bg)] text-[var(--ui-button-primary-fg)] shadow-none hover:bg-[var(--ui-button-primary-hover)]",
        destructive: "bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive/90",
        outline: "border border-[var(--ui-border)] bg-[var(--ui-surface)] text-[var(--ui-text-soft)] hover:bg-[var(--ui-muted)] hover:text-[var(--ui-text)]",
        secondary: "border border-[var(--ui-border-accent)] bg-[var(--ui-accent)] text-[var(--ui-accent-strong)] hover:bg-[var(--ui-accent)]",
        ghost: "text-[var(--ui-text-muted)] hover:bg-[var(--ui-muted)] hover:text-[var(--ui-text)]",
        link: "text-[var(--ui-accent-strong)] underline-offset-4 hover:underline",
        gradient: "bg-[var(--ui-button-primary-bg)] text-[var(--ui-button-primary-fg)] shadow-none hover:bg-[var(--ui-button-primary-hover)]",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-8 px-3 text-xs",
        lg: "h-12 px-6 text-base",
        icon: "h-10 w-10",
        "icon-sm": "h-8 w-8",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
