import { cva } from "class-variance-authority"

// Kept apart from tabs.tsx so that file only exports components (fast refresh)
export const tabsListVariants = cva("items-center text-muted-foreground", {
  variants: {
    variant: {
      default: "inline-flex h-10 justify-center rounded-md bg-muted p-1",
      // redesign: pill bar; full width with equal tabs below md (no horizontal scroll)
      pill: "flex w-full gap-1 rounded-full bg-accent p-1 md:inline-flex md:w-auto",
    },
  },
  defaultVariants: { variant: "default" },
})

export const tabsTriggerVariants = cva(
  "inline-flex items-center justify-center whitespace-nowrap text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 data-[state=active]:text-foreground",
  {
    variants: {
      variant: {
        default: "rounded-sm px-3 py-1.5 data-[state=active]:bg-background data-[state=active]:shadow-sm",
        pill: "min-w-0 flex-1 basis-0 truncate rounded-full px-1.5 py-[7px] text-[13px] data-[state=active]:bg-background md:flex-none md:basis-auto md:px-3.5 md:py-1.5 md:text-sm",
      },
    },
    defaultVariants: { variant: "default" },
  },
)
