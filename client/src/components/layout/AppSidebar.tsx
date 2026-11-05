import { Link, useLocation } from "react-router-dom"
import { isPeladaPath } from "@/utils/peladaAreas"
import { HugeiconsIcon } from "@hugeicons/react"
import { Home01Icon, PlusSignIcon, SidebarLeftIcon } from "@hugeicons/core-free-icons"
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
import { NotificationBell } from "@/components/notifications/NotificationBell"
import { collapsedColumn, collapsedTile } from "@/components/layout/sidebarTile"
import { cn } from "@/lib/utils"

export function AppSidebar() {
  const { pathname } = useLocation()
  const { peladas, loading, openCreatePelada } = useMyPeladasContext()
  const { isMobile, state, toggleSidebar, setOpenMobile } = useSidebar()
  const closeOnMobile = () => setOpenMobile(false)

  return (
    <Sidebar collapsible="icon" className="border-sidebar-border">
      <SidebarHeader className="h-[60px] flex-row items-center gap-1.5 px-3.5 py-4 group-data-[collapsible=icon]:h-auto group-data-[collapsible=icon]:flex-col-reverse group-data-[collapsible=icon]:items-start group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:gap-1 group-data-[collapsible=icon]:px-3 group-data-[collapsible=icon]:py-3">
        <Link
          to="/home"
          onClick={closeOnMobile}
          className="flex min-w-0 flex-1 items-center gap-2.5 group-data-[collapsible=icon]:hidden"
        >
          <img src="/logo-64.png" alt="" width={30} height={30} className="size-[30px] shrink-0 rounded-full object-cover" />
          <span className="text-brand-text text-[17px] font-bold">FutSpring</span>
        </Link>
        {/* on mobile the bell lives in the top bar */}
        {!isMobile && (
          <NotificationBell
            compact={state === "collapsed"}
            className="size-7 rounded-lg hover:bg-sidebar-accent group-data-[collapsible=icon]:size-10 [&_svg]:size-4"
          />
        )}
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleSidebar}
          aria-label={isMobile ? "Fechar menu" : "Recolher menu"}
          className="size-7 shrink-0 rounded-lg text-muted-foreground hover:bg-sidebar-accent hover:text-foreground group-data-[collapsible=icon]:size-10"
        >
          <HugeiconsIcon icon={SidebarLeftIcon} className="size-4" />
        </Button>
      </SidebarHeader>

      <SidebarContent className="gap-0">
        <SidebarGroup className={cn("px-2.5 py-1", collapsedColumn)}>
          <SidebarMenu className="gap-0.5">
            <SidebarMenuItem>
              <SidebarMenuButton
                asChild
                isActive={pathname === "/home"}
                tooltip="Início"
                className={cn(
                  "h-auto gap-2.5 rounded-lg px-2.5 py-2 font-medium text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground data-[active=true]:text-foreground",
                  collapsedTile,
                )}
              >
                <Link to="/home" onClick={closeOnMobile}>
                  <HugeiconsIcon icon={Home01Icon} />
                  <span>Início</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>

        <SidebarGroup className={cn("min-h-0 flex-1 px-2.5 py-0", collapsedColumn)}>
          <div className="flex min-h-[42px] items-center justify-between pb-1.5 pt-4 group-data-[collapsible=icon]:min-h-0 group-data-[collapsible=icon]:justify-start group-data-[collapsible=icon]:pb-1 group-data-[collapsible=icon]:pt-3">
            <SidebarGroupLabel className="h-auto px-2.5 text-xs font-medium text-muted-foreground group-data-[collapsible=icon]:hidden">
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
              className="mr-1 size-[26px] shrink-0 bg-transparent text-muted-foreground hover:bg-sidebar-accent hover:text-foreground group-data-[collapsible=icon]:mr-0 group-data-[collapsible=icon]:size-10 group-data-[collapsible=icon]:rounded-lg group-data-[collapsible=icon]:border-0"
            >
              <HugeiconsIcon icon={PlusSignIcon} className="size-3.5" />
            </Button>
          </div>
          {/* collapsed: no scrollbar, a fixed one would eat the column's width and push the avatars */}
          <SidebarMenu className="min-h-0 flex-1 gap-0.5 overflow-y-auto group-data-[collapsible=icon]:[scrollbar-width:none] group-data-[collapsible=icon]:[&::-webkit-scrollbar]:hidden">
            {loading
              ? Array.from({ length: 3 }, (_, i) => (
                  <SidebarMenuItem key={i}>
                    <SidebarMenuSkeleton
                      showIcon
                      className="h-[50px] group-data-[collapsible=icon]:size-10 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0 group-data-[collapsible=icon]:[&>[data-sidebar=menu-skeleton-icon]]:size-8 group-data-[collapsible=icon]:[&>[data-sidebar=menu-skeleton-text]]:hidden"
                    />
                  </SidebarMenuItem>
                ))
              : peladas.map((pelada) => (
                  <SidebarPeladaItem
                    key={pelada.id}
                    pelada={pelada}
                    isActive={isPeladaPath(pathname, pelada.id)}
                    onNavigate={closeOnMobile}
                  />
                ))}
          </SidebarMenu>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className={cn("p-2", collapsedColumn)}>
        <UserMenu />
      </SidebarFooter>
    </Sidebar>
  )
}
