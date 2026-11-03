import type { ReactNode } from "react"
import { PeladaBreadcrumb, type BreadcrumbEntry } from "@/components/pelada/PeladaBreadcrumb"

interface PageHeaderProps {
  breadcrumb: BreadcrumbEntry[]
  // top right, next to the breadcrumb (the pelada's area selector)
  aside?: ReactNode
  // omitted when another element holds the page's h1 (the pelada overview's banner)
  title?: ReactNode
  description?: ReactNode
  // the page's main action, at the right of the title
  actions?: ReactNode
}

// Breadcrumb, page title (Barlow) and the page's actions: the same header on every private page
export function PageHeader({ breadcrumb, aside, title, description, actions }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <PeladaBreadcrumb items={breadcrumb} />
        {aside}
      </div>
      {(title || actions) && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          {title && <h1 className="font-display text-heading-lg font-bold md:text-display">{title}</h1>}
          {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
        </div>
      )}
      {description && <p className="max-w-2xl text-sm text-muted-foreground md:text-base">{description}</p>}
    </header>
  )
}
