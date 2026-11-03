import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

interface PageContainerProps {
  // default: the app's content column (1120px); narrow: a single settings card (720px)
  width?: "default" | "narrow"
  className?: string
  children: ReactNode
}

// The <main> of every private page: one max width, one pair of side margins, one vertical rhythm,
// so the left edge and the title don't jump when moving between pages
export function PageContainer({ width = "default", className, children }: PageContainerProps) {
  return (
    <main
      className={cn(
        "mx-auto flex w-full min-w-0 flex-col gap-6 px-4 py-5 md:px-8 md:py-7",
        width === "narrow" ? "max-w-[720px]" : "max-w-[1120px]",
        className,
      )}
    >
      {children}
    </main>
  )
}
