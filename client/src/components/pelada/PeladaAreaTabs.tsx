import { Link } from "react-router-dom"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { tabsListVariants, tabsTriggerVariants } from "@/components/ui/tabs-variants"
import { cn } from "@/lib/utils"
import { areaPath, PELADA_AREAS, PRIMARY_PELADA_AREAS, visibleAreas, type PeladaArea, type PeladaRole } from "@/utils/peladaAreas"

interface PeladaAreaTabsProps {
  peladaId: number | string
  current: PeladaArea
  role: PeladaRole
  className?: string
}

export function PeladaAreaTabs({ peladaId, current, role, className }: PeladaAreaTabsProps) {
  const more = visibleAreas(role).filter((area) => !PRIMARY_PELADA_AREAS.includes(area))
  const moreActive = more.includes(current)

  return (
    <nav aria-label="Áreas da pelada" className={className}>
      <div className={cn(tabsListVariants({ variant: "underline" }), "flex w-full gap-0")}>
        {PRIMARY_PELADA_AREAS.map((area) => (
          <Link
            key={area}
            to={areaPath(peladaId, area)}
            aria-label={PELADA_AREAS[area].label}
            aria-current={area === current ? "page" : undefined}
            data-state={area === current ? "active" : undefined}
            className={cn(tabsTriggerVariants({ variant: "underline" }), "min-w-0 flex-1 px-1 text-[13px]")}
          >
            {area === "overview" ? "Geral" : PELADA_AREAS[area].label}
          </Link>
        ))}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              variant="ghost"
              aria-label={moreActive ? `Mais áreas, atual: ${PELADA_AREAS[current].label}` : "Mais áreas"}
              className={cn(tabsTriggerVariants({ variant: "underline" }), "min-w-0 flex-1 gap-1 rounded-none px-1 text-[13px]", moreActive && "border-brand text-foreground")}
            >
              Mais <HugeiconsIcon icon={ArrowDown01Icon} className="size-3.5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-64" aria-label="Outras áreas da pelada">
            {(["Pelada", "Gestão"] as const).map((group) => (
              <DropdownMenuGroup key={group}>
                <DropdownMenuLabel className="text-xs text-muted-foreground">{group}</DropdownMenuLabel>
                {more.filter((area) => PELADA_AREAS[area].group === group).map((area) => (
                  <DropdownMenuItem key={area} asChild className="min-h-11">
                    <Link to={areaPath(peladaId, area)} aria-current={area === current ? "page" : undefined}>
                      {PELADA_AREAS[area].label}
                    </Link>
                  </DropdownMenuItem>
                ))}
              </DropdownMenuGroup>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </nav>
  )
}
