import { useNavigate } from "react-router-dom"
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from "@/components/ui/select"
import { cn } from "@/lib/utils"
import { areaPath, PELADA_AREAS, visibleAreas, type PeladaArea, type PeladaAreaGroup } from "@/utils/peladaAreas"

interface PeladaAreaSelectProps {
  peladaId: number | string
  current: PeladaArea
  isAdmin: boolean
  className?: string
}

const GROUPS: PeladaAreaGroup[] = ["Pelada", "Gestão"]

// The only area switcher inside a pelada: one list, the groups are headings (no submenus)
export function PeladaAreaSelect({ peladaId, current, isAdmin, className }: PeladaAreaSelectProps) {
  const navigate = useNavigate()
  const areas = visibleAreas(isAdmin)

  return (
    <Select value={current} onValueChange={(value) => navigate(areaPath(peladaId, value as PeladaArea))}>
      <SelectTrigger aria-label="Área da pelada" className={cn("h-11 w-full rounded-full sm:h-10 sm:w-[200px]", className)}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent align="end">
        {GROUPS.map((group) => (
          <SelectGroup key={group}>
            <SelectLabel className="text-xs text-subtle-foreground">{group}</SelectLabel>
            {areas
              .filter((area) => PELADA_AREAS[area].group === group)
              .map((area) => (
                <SelectItem key={area} value={area} className="min-h-11 sm:min-h-9">
                  {PELADA_AREAS[area].label}
                </SelectItem>
              ))}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  )
}
