import type { ReactNode } from "react"
import { Link } from "react-router-dom"
import { PageHeader } from "@/components/layout/PageHeader"
import type { BreadcrumbEntry } from "@/components/pelada/PeladaBreadcrumb"
import { tabsListVariants, tabsTriggerVariants } from "@/components/ui/tabs-variants"
import { cn } from "@/lib/utils"

export type PlatformArea = "overview" | "peladas" | "payments" | "users"

const AREAS: Record<PlatformArea, { label: string; to: string }> = {
  overview: { label: "Visão geral", to: "/admin" },
  peladas: { label: "Peladas", to: "/admin/peladas" },
  payments: { label: "Pagamentos", to: "/admin/pagamentos" },
  users: { label: "Usuários", to: "/admin/usuarios" },
}
const ORDER: PlatformArea[] = ["overview", "peladas", "payments", "users"]

interface PlatformPageHeaderProps {
  area: PlatformArea
  title: string
  // entries between the area and the page (the pelada's name on its page)
  trail?: BreadcrumbEntry[]
  description?: ReactNode
  actions?: ReactNode
}

// Header of every panel page: breadcrumb, title and the areas as underlined links (navigation, not a tablist)
export function PlatformPageHeader({ area, title, trail, description, actions }: PlatformPageHeaderProps) {
  const breadcrumb: BreadcrumbEntry[] = [
    { label: "Início", to: "/home" },
    { label: "Admin", to: "/admin" },
    ...(area === "overview" ? [] : [{ label: AREAS[area].label, to: trail ? AREAS[area].to : undefined }]),
    ...(trail ?? []),
  ]
  return (
    <div className="flex flex-col gap-4">
      <PageHeader breadcrumb={breadcrumb} title={title} description={description} actions={actions} />
      <nav aria-label="Áreas do painel" className="-mx-4 overflow-x-auto px-4 scrollbar-none md:mx-0 md:px-0">
        <div className={cn(tabsListVariants({ variant: "underline" }), "flex w-max min-w-full")}>
          {ORDER.map((key) => (
            <Link
              key={key}
              to={AREAS[key].to}
              aria-current={key === area ? "page" : undefined}
              data-state={key === area ? "active" : undefined}
              className={tabsTriggerVariants({ variant: "underline" })}
            >
              {AREAS[key].label}
            </Link>
          ))}
        </div>
      </nav>
    </div>
  )
}
