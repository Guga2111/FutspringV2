import type { ReactNode } from "react"
import { PageHeader } from "@/components/layout/PageHeader"
import { PeladaAreaSelect } from "@/components/pelada/PeladaAreaSelect"
import { PeladaAreaTabs } from "@/components/pelada/PeladaAreaTabs"
import { areaPath, canSeeArea, PELADA_AREAS, type PeladaArea, type PeladaRole } from "@/utils/peladaAreas"

interface PeladaPageHeaderProps {
  peladaId: number | string
  // null while the pelada is still loading
  peladaName: string | null
  area: PeladaArea
  role: PeladaRole
  // the page title; the overview passes none because its banner holds the h1
  title?: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

// PageHeader of a pelada area: Início > Pelada > Área, the area selector and the area's main action. Below md the
// breadcrumb steps aside (the mobile header has ← and the name) and the selector becomes a strip of areas
export function PeladaPageHeader({ peladaId, peladaName, area, role, title, description, actions }: PeladaPageHeaderProps) {
  const name = peladaName ?? "Pelada"
  // on a page the caller can't open (access denied) the current area isn't in their list
  const showSelect = canSeeArea(area, role)
  const header = (
    <PageHeader
      breadcrumb={[
        { label: "Início", to: "/home" },
        { label: name, to: areaPath(peladaId, "overview"), truncate: true },
        { label: PELADA_AREAS[area].label },
      ]}
      aside={showSelect && <PeladaAreaSelect peladaId={peladaId} current={area} role={role} className="hidden md:flex md:shrink-0" />}
      title={title}
      description={description}
      actions={actions}
    />
  )
  if (!showSelect) return header
  return (
    <div className="flex flex-col gap-4">
      <PeladaAreaTabs peladaId={peladaId} current={area} role={role} className="md:hidden" />
      {header}
    </div>
  )
}
