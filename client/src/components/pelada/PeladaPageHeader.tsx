import type { ReactNode } from "react"
import { PageHeader } from "@/components/layout/PageHeader"
import { PeladaAreaSelect } from "@/components/pelada/PeladaAreaSelect"
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

// PageHeader of a pelada area: Início > Pelada > Área, the area selector and the area's main action
export function PeladaPageHeader({ peladaId, peladaName, area, role, title, description, actions }: PeladaPageHeaderProps) {
  const name = peladaName ?? "Pelada"
  // on a page the caller can't open (access denied) the current area isn't in their list
  const showSelect = canSeeArea(area, role)
  return (
    <PageHeader
      breadcrumb={[
        { label: "Início", to: "/home" },
        { label: name, to: areaPath(peladaId, "overview"), truncate: true },
        { label: PELADA_AREAS[area].label },
      ]}
      aside={showSelect && <PeladaAreaSelect peladaId={peladaId} current={area} role={role} className="sm:shrink-0" />}
      title={title}
      description={description}
      actions={actions}
    />
  )
}
