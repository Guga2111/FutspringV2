import { useEffect } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"
import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { Home01Icon, Notification01Icon, PlusSignIcon, SportShoeIcon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { useAuth } from "@/hooks/useAuth"
import { useNotificationsContext } from "@/hooks/useNotificationsContext"
import { useVirtualKeyboard } from "@/hooks/useVirtualKeyboard"
import { isChatPath, isTabRoot, tabForPath, tabRoot, type MobileTab } from "@/utils/mobileNav"
import { cn } from "@/lib/utils"

const TAB_LABEL: Record<MobileTab, string> = {
  home: "Início",
  peladas: "Peladas",
  notifications: "Avisos",
  profile: "Perfil",
}

const TAB_ICON: Record<Exclude<MobileTab, "profile">, IconSvgElement> = {
  home: Home01Icon,
  peladas: SportShoeIcon,
  notifications: Notification01Icon,
}

function unreadLabel(count: number | null): string | null {
  if (!count) return null
  return count > 20 ? "20+" : String(count)
}

// Route navigation needs real links and a separate create action, rather than shadcn's in-page Tabs widget.
// Floating below md; --mobile-tabbar-h reserves its full footprint, including the bottom gap and safe area.
export function BottomTabBar({ onCreate }: { onCreate: () => void }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { unreadCount } = useNotificationsContext()
  const keyboard = useVirtualKeyboard()
  const userId = user?.id ?? null
  const active = tabForPath(pathname, userId)
  const hidden = keyboard || isChatPath(pathname)

  useEffect(() => {
    const root = document.documentElement
    if (hidden) root.dataset.tabbar = "hidden"
    else delete root.dataset.tabbar
    return () => {
      delete root.dataset.tabbar
    }
  }, [hidden])

  if (hidden) return null

  // the active tab again: back to its own page, or to the top of it (Instagram, X)
  function handleTab(event: React.MouseEvent, tab: MobileTab) {
    if (tab !== active) return
    event.preventDefault()
    if (isTabRoot(pathname, userId)) window.scrollTo({ top: 0, behavior: "smooth" })
    else navigate(tabRoot(tab, userId))
  }

  const unread = unreadLabel(unreadCount)

  function tab(name: MobileTab) {
    const current = active === name
    const label = name === "notifications" && unread ? `${TAB_LABEL[name]}, ${unreadCount} não lidas` : TAB_LABEL[name]
    return (
      <Link
        key={name}
        to={tabRoot(name, userId)}
        aria-current={current ? "page" : undefined}
        aria-label={label}
        onClick={(event) => handleTab(event, name)}
        className={cn(
          "flex min-h-12 min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-full text-caption transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          current ? "bg-muted font-semibold text-brand-text" : "text-muted-foreground hover:bg-muted/60 hover:text-foreground active:bg-muted",
        )}
      >
        <div className="relative flex size-6 items-center justify-center">
          {name === "profile" && user ? (
            <PlayerAvatar
              userId={user.id}
              username={user.username}
              image={user.image}
              className={cn("size-6 text-[10px]", current && "ring-2 ring-brand")}
            />
          ) : name !== "profile" ? (
            <HugeiconsIcon icon={TAB_ICON[name]} className="size-6" strokeWidth={current ? 2.2 : 1.5} />
          ) : null}
          {name === "notifications" && unread && (
            <Badge
              variant="status"
              aria-hidden
              className="absolute -right-2.5 -top-1.5 flex h-[18px] min-w-[18px] items-center justify-center bg-brand px-1 py-0 text-[10px] font-bold leading-none text-brand-foreground"
            >
              {unread}
            </Badge>
          )}
        </div>
        <span aria-hidden>{TAB_LABEL[name]}</span>
      </Link>
    )
  }

  return (
    <nav
      aria-label="Navegação principal"
      className="fixed inset-x-3 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-40 rounded-full bg-card p-1 shadow-fab md:hidden"
    >
      <div className="flex h-14 items-stretch">
        {tab("home")}
        {tab("peladas")}
        <div className="flex flex-1 items-center justify-center">
          {/* a 44 px brand circle inside a 48 px target */}
          <Button
            variant="ghost"
            size="icon"
            aria-label="Criar"
            aria-haspopup="dialog"
            onClick={onCreate}
            className="group size-12 hover:bg-transparent"
          >
            <span className="flex size-11 items-center justify-center rounded-full bg-brand text-brand-foreground transition-colors group-hover:bg-brand-hover group-active:scale-95 [&_svg]:size-6">
              <HugeiconsIcon icon={PlusSignIcon} strokeWidth={2.2} />
            </span>
          </Button>
        </div>
        {tab("notifications")}
        {tab("profile")}
      </div>
    </nav>
  )
}
