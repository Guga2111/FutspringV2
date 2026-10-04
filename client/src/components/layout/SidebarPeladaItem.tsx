import { Link } from "react-router-dom"
import { SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { formatNextSessionShort } from "@/utils/dates"
import type { PeladaResponse } from "@/types/pelada"

interface SidebarPeladaItemProps {
  pelada: PeladaResponse
  isActive: boolean
  onNavigate: () => void
}

export function SidebarPeladaItem({ pelada, isActive, onNavigate }: SidebarPeladaItemProps) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isActive}
        tooltip={pelada.name}
        className="h-auto gap-2.5 rounded-[10px] p-2 font-normal hover:bg-sidebar-accent/70 data-[active=true]:font-normal group-data-[collapsible=icon]:!size-auto group-data-[collapsible=icon]:!p-1"
      >
        <Link to={`/pelada/${pelada.id}`} onClick={onNavigate}>
          <PeladaAvatar name={pelada.name} image={pelada.image} className="size-[34px] rounded-[9px] text-xs" />
          <span className="flex min-w-0 flex-col gap-0.5 group-data-[collapsible=icon]:hidden">
            <span className="truncate text-sm font-medium">{pelada.name}</span>
            <span className="truncate text-xs text-subtle-foreground">{formatNextSessionShort(pelada.nextDaily)}</span>
          </span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  )
}
