import { useNavigate } from "react-router-dom"
import { Bell, ChevronsUpDown, LogOut, Moon, Sun, User } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
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
import { getFileUrl, getInitials } from "@/lib/utils"

function UserAvatar({ username, image }: { username: string; image: string | null }) {
  return (
    <Avatar className="size-8 shrink-0 rounded-lg">
      <AvatarImage src={getFileUrl(image)} alt="" className="object-cover" />
      <AvatarFallback className="rounded-lg bg-avatar-fallback text-xs font-semibold">
        {getInitials(username)}
      </AvatarFallback>
    </Avatar>
  )
}

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
              className="h-auto gap-2.5 rounded-[10px] p-2 data-[state=open]:bg-sidebar-accent group-data-[collapsible=icon]:!size-auto group-data-[collapsible=icon]:!p-1"
            >
              <UserAvatar username={user.username} image={user.image} />
              <span className="flex min-w-0 flex-1 flex-col group-data-[collapsible=icon]:hidden">
                <span className="truncate text-sm font-semibold">{user.username}</span>
                <span className="truncate text-xs text-muted-foreground">{user.email}</span>
              </span>
              <ChevronsUpDown className="ml-auto text-muted-foreground group-data-[collapsible=icon]:hidden" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            side={isMobile ? "top" : "right"}
            align={isMobile ? "start" : "end"}
            sideOffset={isMobile ? 4 : 8}
            className="w-60 rounded-[10px] p-1 shadow-menu"
          >
            <DropdownMenuLabel className="flex items-center gap-2.5 p-2 font-normal">
              <UserAvatar username={user.username} image={user.image} />
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-sm font-semibold">{user.username}</span>
                <span className="truncate text-xs text-muted-foreground">{user.email}</span>
              </span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator className="-mx-1 bg-border" />
            <DropdownMenuItem className={itemClass} onSelect={goToProfile}>
              <User />
              Perfil
            </DropdownMenuItem>
            <DropdownMenuItem className={itemClass} disabled>
              <Bell />
              Notificações
              <Badge variant="status" className="ml-auto bg-secondary px-2 py-0 text-[11px] text-secondary-foreground">
                Em breve
              </Badge>
            </DropdownMenuItem>
            <DropdownMenuItem className={itemClass} onSelect={toggleTheme}>
              {isDark ? <Sun /> : <Moon />}
              {isDark ? "Tema claro" : "Tema escuro"}
            </DropdownMenuItem>
            <DropdownMenuSeparator className="-mx-1 bg-border" />
            <DropdownMenuItem
              className={`${itemClass} text-destructive focus:bg-destructive-muted focus:text-destructive [&>svg]:text-destructive`}
              onSelect={handleLogout}
            >
              <LogOut />
              Sair
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
