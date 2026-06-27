"use client"

import * as React from "react"
import { cn } from "@/lib/utils"

interface SwitchProps extends React.InputHTMLAttributes<HTMLInputElement> {
  onCheckedChange?: (checked: boolean) => void
}

const Switch = React.forwardRef<HTMLInputElement, SwitchProps>(
  ({ className, checked, onCheckedChange, ...props }, ref) => {
    return (
      <label
        className={cn(
          "relative inline-flex items-center cursor-pointer",
          className
        )}
      >
        <input
          type="checkbox"
          className="sr-only peer"
          ref={ref}
          checked={checked}
          onChange={(e) => onCheckedChange?.(e.target.checked)}
          {...props}
        />
        <div className="w-9 h-5 rounded-full bg-[var(--ui-muted)] transition-all duration-200 after:absolute after:start-[2px] after:top-0.5 after:h-4 after:w-4 after:rounded-full after:bg-[var(--ui-surface)] after:shadow-sm after:transition-all after:content-[''] peer peer-checked:bg-[var(--ui-accent-strong)] peer-checked:after:translate-x-full peer-focus:ring-2 peer-focus:ring-ring" />
      </label>
    )
  }
)
Switch.displayName = "Switch"

export { Switch }
