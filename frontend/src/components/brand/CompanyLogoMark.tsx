import { type HTMLAttributes } from "react"
import { cn } from "@/lib/utils"

interface CompanyLogoMarkProps extends HTMLAttributes<HTMLDivElement> {
  decorative?: boolean
}

export default function CompanyLogoMark({
  className,
  decorative = false,
  ...props
}: CompanyLogoMarkProps) {
  return (
    <div
      className={cn("company-logo-mark relative inline-block overflow-visible", className)}
      role={decorative ? undefined : "img"}
      aria-label={decorative ? undefined : "JC Display Packaging"}
      aria-hidden={decorative || undefined}
      {...props}
    >
      <img
        src="/logo-leaf-blue.png"
        alt=""
        className="company-logo-mark__blue absolute select-none"
        draggable={false}
      />
      <img
        src="/logo-leaf-green.png"
        alt=""
        className="company-logo-mark__green absolute select-none"
        draggable={false}
      />
    </div>
  )
}
