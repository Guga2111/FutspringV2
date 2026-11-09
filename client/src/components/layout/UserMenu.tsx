import { useNavigate } from "react-router-dom"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { HugeiconsIcon } from "@hugeicons/react"
import { DashboardSquare01Icon, Logout01Icon, Moon02Icon, Settings01Icon, Sun03Icon, UnfoldMoreIcon, UserIcon } from "@hugeicons/core-free-icons"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from "@/components/ui/sidebar"
import { useSidebar } from "@/components/ui/sidebar-context"
import { useAuth } from "@/hooks/useAuth"
import { useTheme } from "@/hooks/useTheme"
import { collapsedTile } from "@/components/layout/sidebarTile"
import { cn } from "@/lib/utils"

// the square corners mark the account menu; the color is the player's own, as on every screen
const userAvatarClass = "size-8 rounded-lg text-xs [&>span]:rounded-lg"

const itemClass = "gap-2.5 rounded-md px-2 py-[7px] text-sm [&>svg]:size-4 [&>svg]:text-muted-foreground"

// Sidebar footer: the logged user and their menu (opens to the right; above the button on mobile)
export function UserMenu() {
  const { user, logout } = useAuth()
  const { isDark, toggleTheme } = useTheme()
  const { isMobile, setOpenMobile } = useSidebar()
  const navigate = useNavigate()

  if (!user) return null

  function goToProfile() {
    if (!user) return
    setOpenMobile(false)
    navigate(`/profile/${user.id}`)
  }

  function goToSettings() {
    setOpenMobile(false)
    navigate("/settings")
  }

  function goToAdmin() {
    setOpenMobile(false)
    navigate("/admin")
  }

  function handleLogout() {
    logout()
    navigate("/auth", { replace: true })
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              aria-label="Menu do usuário"
              className={cn("h-auto gap-2.5 rounded-[10px] p-2 data-[state=open]:bg-sidebar-accent", collapsedTile)}
            >
              <PlayerAvatar userId={user.id} username={user.username} image={user.image} className={userAvatarClass} />
              <span className="flex min-w-0 flex-1 flex-col group-data-[collapsible=icon]:hidden">
                <span className="truncate text-sm font-semibold">{user.username}</span>
                <span className="truncate text-xs text-muted-foreground">{user.email}</span>
              </span>
              <HugeiconsIcon icon={UnfoldMoreIcon} className="ml-auto text-muted-foreground group-data-[collapsible=icon]:hidden" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            side={isMobile ? "top" : "right"}
            align={isMobile ? "start" : "end"}
            sideOffset={isMobile ? 4 : 8}
            className="w-60 rounded-[10px] p-1 shadow-menu"
          >
            <DropdownMenuLabel className="flex items-center gap-2.5 p-2 font-normal">
              <PlayerAvatar userId={user.id} username={user.username} image={user.image} className={userAvatarClass} />
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-sm font-semibold">{user.username}</span>
                <span className="truncate text-xs text-muted-foreground">{user.email}</span>
              </span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator className="-mx-1 bg-border" />
            <DropdownMenuItem className={itemClass} onSelect={goToProfile}>
              <HugeiconsIcon icon={UserIcon} />
              Perfil
            </DropdownMenuItem>
            {/* the platform operator's panel; the API is the authority (404 to anyone else) */}
            {user.isPlatformAdmin && (
              <DropdownMenuItem className={itemClass} onSelect={goToAdmin}>
                <HugeiconsIcon icon={DashboardSquare01Icon} />
                Admin
              </DropdownMenuItem>
            )}
            <DropdownMenuItem className={itemClass} onSelect={goToSettings}>
              <HugeiconsIcon icon={Settings01Icon} />
              Configurações
            </DropdownMenuItem>
            <DropdownMenuItem className={itemClass} onSelect={toggleTheme}>
              {isDark ? <HugeiconsIcon icon={Sun03Icon} /> : <HugeiconsIcon icon={Moon02Icon} />}
              {isDark ? "Tema claro" : "Tema escuro"}
            </DropdownMenuItem>
            <DropdownMenuSeparator className="-mx-1 bg-border" />
            <DropdownMenuItem
              className={`${itemClass} text-destructive focus:bg-destructive-muted focus:text-destructive [&>svg]:text-destructive`}
              onSelect={handleLogout}
            >
              <HugeiconsIcon icon={Logout01Icon} />
              Sair
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
