import { Link } from "react-router-dom"
import { SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { formatNextSessionShort } from "@/utils/dates"
import type { PeladaResponse } from "@/types/pelada"
import { collapsedTile } from "@/components/layout/sidebarTile"
import { cn } from "@/lib/utils"

interface SidebarPeladaItemProps {
  pelada: PeladaResponse
  isActive: boolean
  onNavigate: () => void
}

export function SidebarPeladaItem({ pelada, isActive, onNavigate }: SidebarPeladaItemProps) {
  const next = pelada.nextDaily
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isActive}
        tooltip={pelada.name}
        className={cn("h-auto gap-2.5 rounded-[10px] p-2 font-normal hover:bg-sidebar-accent/70 data-[active=true]:font-normal", collapsedTile)}
      >
        <Link to={`/pelada/${pelada.id}`} onClick={onNavigate}>
          <PeladaAvatar name={pelada.name} image={pelada.image} className="size-[34px] rounded-[9px] text-xs group-data-[collapsible=icon]:size-8 group-data-[collapsible=icon]:rounded-lg" />
          <span className="flex min-w-0 flex-col gap-0.5 group-data-[collapsible=icon]:hidden">
            <span className="truncate text-sm font-medium">{pelada.name}</span>
            <span className="flex min-w-0 gap-1 text-xs text-subtle-foreground">
              <span className="truncate">{formatNextSessionShort(pelada.nextDaily)}</span>
              {next && (
                // the spots never truncate: the date gives way first
                <span className="shrink-0 tabular-nums">
                  <span aria-hidden>· {next.confirmedCount}/{next.capacity}</span>
                  <span className="sr-only">, {next.confirmedCount} de {next.capacity} vagas confirmadas</span>
                </span>
              )}
            </span>
          </span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
}
