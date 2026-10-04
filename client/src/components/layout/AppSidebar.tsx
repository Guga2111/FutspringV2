import { Link, useLocation } from "react-router-dom"
import { Home, PanelLeft, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from "@/components/ui/sidebar"
import { useSidebar } from "@/components/ui/sidebar-context"
import { useMyPeladasContext } from "@/hooks/useMyPeladasContext"
import { SidebarPeladaItem } from "@/components/layout/SidebarPeladaItem"
import { UserMenu } from "@/components/layout/UserMenu"

export function AppSidebar() {
  const { pathname } = useLocation()
  const { peladas, loading, openCreatePelada } = useMyPeladasContext()
  const { isMobile, toggleSidebar, setOpenMobile } = useSidebar()
  const closeOnMobile = () => setOpenMobile(false)

  return (
    <Sidebar collapsible="icon" className="border-sidebar-border">
      <SidebarHeader className="h-[60px] flex-row items-center gap-2.5 px-3.5 py-4 group-data-[collapsible=icon]:justify-center">
        <Link
          to="/home"
          onClick={closeOnMobile}
          className="flex min-w-0 flex-1 items-center gap-2.5 group-data-[collapsible=icon]:hidden"
        >
          <img src="/logo-64.png" alt="" width={30} height={30} className="size-[30px] shrink-0 rounded-full object-cover" />
          <span className="text-gradient-primary text-[17px] font-bold">FutSpring</span>
        </Link>
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleSidebar}
          aria-label={isMobile ? "Fechar menu" : "Recolher menu"}
          className="size-7 shrink-0 rounded-lg text-muted-foreground hover:bg-sidebar-accent hover:text-foreground"
        >
          <PanelLeft className="size-4" />
        </Button>
      </SidebarHeader>

      <SidebarContent className="gap-0">
        <SidebarGroup className="px-2.5 py-1">
          <SidebarMenu className="gap-0.5">
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                isActive={pathname === "/home"}
                tooltip="Início"
                className="h-auto gap-2.5 rounded-lg px-2.5 py-2 font-medium text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground data-[active=true]:text-foreground group-data-[collapsible=icon]:!size-auto group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:!px-2.5 group-data-[collapsible=icon]:!py-2"
              >
                <Link to="/home" onClick={closeOnMobile}>
                  <Home />
                  <span>Início</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>

        <SidebarGroup className="min-h-0 flex-1 px-2.5 py-0">
          <div className="flex min-h-[42px] items-center justify-between pb-1.5 pt-4 group-data-[collapsible=icon]:justify-center">
            <SidebarGroupLabel className="h-auto px-2.5 text-[11px] font-semibold uppercase tracking-[0.06em] text-faint-foreground group-data-[collapsible=icon]:hidden">
              Minhas peladas
            </SidebarGroupLabel>
            <Button
              variant="outline"
              size="icon"
              onClick={() => {
                closeOnMobile()
                openCreatePelada()
              }}
              aria-label="Nova pelada"
              className="mr-1 size-[26px] shrink-0 bg-transparent text-muted-foreground hover:bg-sidebar-accent hover:text-foreground group-data-[collapsible=icon]:mr-0"
            >
              <Plus className="size-3.5" />
            </Button>
          </div>
          <SidebarMenu className="min-h-0 flex-1 gap-0.5 overflow-y-auto">
            {loading
              ? Array.from({ length: 3 }, (_, i) => (
                  <SidebarMenuItem key={i}>
                    <SidebarMenuSkeleton showIcon className="h-[50px]" />
                  </SidebarMenuItem>
                ))
              : peladas.map((pelada) => (
                  <SidebarPeladaItem
                    key={pelada.id}
                    pelada={pelada}
                    isActive={pathname === `/pelada/${pelada.id}`}
                    onNavigate={closeOnMobile}
                  />
                ))}
          </SidebarMenu>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="p-2">
        <UserMenu />
      </SidebarFooter>
    </Sidebar>
  )
}
