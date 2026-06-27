import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-bold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "border-transparent bg-[#171916] text-primary-foreground",
        secondary: "border-[#E4E8E5] bg-[#F4F6F5] text-[#343A35]",
        destructive: "border-transparent bg-destructive text-destructive-foreground",
        outline: "text-foreground",
        success: "border-[#D7E8E0] bg-[#EEF7F3] text-[#1F5F53]",
        warning: "border-[#DDE4DF] bg-[#F4F6F5] text-[#516B63]",
        purple: "border-[#D7E8E0] bg-[#EEF7F3] text-[#1F5F53]",
        blue: "border-[#DCE4E8] bg-[#F0F5F6] text-[#516B76]",
        slate: "border-[#E4E8E5] bg-[#F4F6F5] text-[#74766F]",
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
