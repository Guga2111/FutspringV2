import type { ReactNode } from "react"
import { PageHeader } from "@/components/layout/PageHeader"
import { PeladaAreaSelect } from "@/components/pelada/PeladaAreaSelect"
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
  actions?: ReactNode
}

// PageHeader of a pelada area: Início > Pelada > Área, the area selector and the area's main action
export function PeladaPageHeader({ peladaId, peladaName, area, isAdmin, title, description, actions }: PeladaPageHeaderProps) {
  const name = peladaName ?? "Pelada"
  // on an admin-only page opened by a member (access denied) the current area isn't in their list
  const showSelect = isAdmin || !PELADA_AREAS[area].adminOnly
  return (
    <PageHeader
      breadcrumb={[
        { label: "Início", to: "/home" },
        { label: name, to: areaPath(peladaId, "overview"), truncate: true },
        { label: PELADA_AREAS[area].label },
      ]}
      aside={showSelect && <PeladaAreaSelect peladaId={peladaId} current={area} isAdmin={isAdmin} className="sm:shrink-0" />}
      title={title}
      description={description}
      actions={actions}
    />
  )
}
