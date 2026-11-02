import { Fragment } from "react"
import { Link } from "react-router-dom"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { cn } from "@/lib/utils"

export interface BreadcrumbEntry {
  label: string
  // ancestors link somewhere; the last entry (the current page) is plain text
  to?: string
  // long names (the pelada) shrink first so the current page stays visible
  truncate?: boolean
}

export function PeladaBreadcrumb({ items, className }: { items: BreadcrumbEntry[]; className?: string }) {
  return (
    <Breadcrumb aria-label="Você está em" className={cn("min-w-0", className)}>
      <BreadcrumbList className="flex-nowrap gap-1.5 text-[13px] sm:gap-2">
        {items.map((item, index) => {
          const last = index === items.length - 1
          const textClass = cn(item.truncate ? "min-w-0 truncate" : "shrink-0 whitespace-nowrap")
          return (
            <Fragment key={`${index}-${item.label}`}>
              {index > 0 && <BreadcrumbSeparator className="shrink-0 text-faint-foreground">&gt;</BreadcrumbSeparator>}
              <BreadcrumbItem className={cn(item.truncate ? "min-w-0" : "shrink-0", last && "min-w-0")}>
                {last || !item.to ? (
                  <BreadcrumbPage className={cn(textClass, last && "min-w-0 truncate")}>{item.label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild className={cn(textClass, "rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring")}>
                    <Link to={item.to} title={item.truncate ? item.label : undefined}>
                      {item.label}
                    </Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </Fragment>
          )
        })}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
