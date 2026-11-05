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
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from "@/components/ui/sidebar"
import { useSidebar } from "@/components/ui/sidebar-context"
import { useMyInvitationsContext } from "@/hooks/useMyInvitationsContext"
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
  // pending invitations are answered on Home, so their count sits on "Início"
  const invitations = useMyInvitationsContext()
  const inviteCount = invitations.items.length
  const inviteLabel = `${inviteCount}${invitations.hasMore ? "+" : ""}`
  const inviteText = inviteCount ? `${inviteLabel} ${inviteCount === 1 && !invitations.hasMore ? "convite pendente" : "convites pendentes"}` : null

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
                tooltip={inviteText ? `Início · ${inviteText}` : "Início"}
                className={cn(
                  "h-auto gap-2.5 rounded-lg px-2.5 py-2 font-medium text-muted-foreground hover:bg-sidebar-accent/70 hover:text-foreground data-[active=true]:text-foreground",
                  collapsedTile,
                )}
              >
                <Link to="/home" onClick={closeOnMobile}>
                  <HugeiconsIcon icon={Home01Icon} />
                  <span>
                    Início
                    {inviteText && <span className="sr-only">, {inviteText}</span>}
                  </span>
                </Link>
              </SidebarMenuButton>
              {inviteText && (
                <>
                  <SidebarMenuBadge aria-hidden className="right-2 top-1/2 -translate-y-1/2 rounded-full peer-data-[size=default]/menu-button:top-1/2 bg-brand px-1.5 text-brand-foreground peer-hover/menu-button:text-brand-foreground peer-data-[active=true]/menu-button:text-brand-foreground">
                    {inviteLabel}
                  </SidebarMenuBadge>
                  {/* collapsed: a dot on the tile instead of the count */}
                  <span aria-hidden className="pointer-events-none absolute left-7 top-1.5 hidden size-2 rounded-full bg-brand group-data-[collapsible=icon]:block" />
                </>
              )}
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
