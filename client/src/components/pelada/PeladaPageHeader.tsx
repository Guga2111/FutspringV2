import type { ReactNode } from "react"
import { PeladaAreaSelect } from "@/components/pelada/PeladaAreaSelect"
import { PeladaBreadcrumb } from "@/components/pelada/PeladaBreadcrumb"
import { areaPath, PELADA_AREAS, type PeladaArea } from "@/utils/peladaAreas"

interface PeladaPageHeaderProps {
  peladaId: number | string
  // null while the pelada is still loading
  peladaName: string | null
  area: PeladaArea
  isAdmin: boolean
  // the page title; the overview passes none because its banner holds the h1
  title?: ReactNode
  description?: ReactNode
}

// Breadcrumb (Início > Pelada > Área), the page title and the area selector
export function PeladaPageHeader({
  peladaId,
  peladaName,
  area,
  isAdmin,
  title,
  description,
}: PeladaPageHeaderProps) {
  const name = peladaName ?? "Pelada"
  // on an admin-only page opened by a member (access denied) the current area isn't in their list
  const showSelect = isAdmin || !PELADA_AREAS[area].adminOnly
  return (
    <header className="flex flex-col gap-3">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <PeladaBreadcrumb
          items={[
            { label: "Início", to: "/home" },
            { label: name, to: areaPath(peladaId, "overview"), truncate: true },
            { label: PELADA_AREAS[area].label },
          ]}
        />
        {showSelect && <PeladaAreaSelect peladaId={peladaId} current={area} isAdmin={isAdmin} className="sm:shrink-0" />}
      </div>
      {title && <h1 className="font-display text-heading-lg font-bold md:text-display">{title}</h1>}
      {description && <p className="max-w-2xl text-sm text-muted-foreground md:text-base">{description}</p>}
    </header>
  )
}
