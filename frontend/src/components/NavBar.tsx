import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Sun, Moon, Menu } from 'lucide-react'
import { useState, useEffect } from 'react'
import { useAuth } from '../hooks/useAuth'
import { Button } from './ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from './ui/dropdown-menu'
import { Avatar, AvatarImage, AvatarFallback } from './ui/avatar'
import { getFileUrl } from '../lib/utils'
import {
  Sheet,
  SheetContent,
  SheetTrigger,
  SheetClose,
} from './ui/sheet'

export default function NavBar() {
  const { logout, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  )
  const [sheetOpen, setSheetOpen] = useState(false)

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains('dark'))
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  function handleToggleDark() {
    document.documentElement.classList.toggle('dark')
    const nowDark = document.documentElement.classList.contains('dark')
    setIsDark(nowDark)
    localStorage.setItem('theme', nowDark ? 'dark' : 'light')
  }

  function handleLogout() {
    logout()
    navigate('/auth', { replace: true })
  }

  const initials = user
    ? user.username.slice(0, 2).toUpperCase()
    : '?'

  const isHomeActive = location.pathname === '/home'

  return (
    <nav className="sticky top-0 z-50 flex items-center justify-between px-6 py-3 border-b backdrop-blur-sm bg-background/80">
      {/* Left: logo */}
      <Link to="/home" className="flex items-center gap-2 font-bold text-lg">
        <img src="/gerrard.png" alt="FutSpring" className="h-7 w-7 rounded-full object-cover" />
        <span className="text-gradient-primary">FutSpring</span>
      </Link>

      {/* Center: nav links (desktop only) */}
      <div className="hidden sm:flex items-center gap-6">
        <Link
          to="/home"
          className={`text-sm transition-colors hover:text-foreground ${
            isHomeActive
              ? 'font-bold underline'
              : 'font-medium text-muted-foreground'
          }`}
        >
          Início
        </Link>
      </div>

      {/* Right: dark mode toggle + avatar dropdown (desktop) + hamburger (mobile) */}
      <div className="flex items-center gap-2">
        {/* Desktop: dark mode toggle + avatar */}
        <Button
          variant="ghost"
          size="icon"
          aria-label="Toggle dark mode"
          onClick={handleToggleDark}
          className="hidden sm:inline-flex"
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>

        <div className="hidden sm:flex">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button
                className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                aria-label="User menu"
              >
                <Avatar className="h-8 w-8 cursor-pointer">
                  {user?.image && (
                    <AvatarImage src={getFileUrl(user.image)} alt={user.username} />
                  )}
                  <AvatarFallback>{initials}</AvatarFallback>
                </Avatar>
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel className="font-normal">
                <div className="flex flex-col space-y-1">
                  <p className="text-sm font-semibold leading-none">{user?.username}</p>
                  <p className="text-xs leading-none text-muted-foreground">{user?.email}</p>
                </div>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem asChild>
                <Link to={`/profile/${user?.id}`} className="cursor-pointer w-full">
                  Ver Perfil
                </Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                className="cursor-pointer text-destructive focus:text-destructive"
                onClick={handleLogout}
              >
                Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        {/* Mobile: hamburger button */}
        <Sheet open={sheetOpen} onOpenChange={setSheetOpen}>
          <SheetTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Open menu"
              className="block sm:hidden"
            >
              <Menu className="h-5 w-5" />
            </Button>
          </SheetTrigger>
          <SheetContent side="right" className="flex flex-col gap-6 pt-10">
            {/* Nav link */}
            <SheetClose asChild>
              <Link
                to="/home"
                className={`text-base transition-colors hover:text-foreground ${
                  isHomeActive
                    ? 'font-bold underline'
                    : 'font-medium text-muted-foreground'
                }`}
              >
                Início
              </Link>
            </SheetClose>

            {/* Dark mode toggle */}
            <button
              className="flex items-center gap-2 text-base font-medium text-foreground hover:text-green-500 transition-colors"
              onClick={() => { handleToggleDark(); setSheetOpen(false) }}
              aria-label="Toggle dark mode"
            >
              {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
              {isDark ? 'Modo Claro' : 'Modo Escuro'}
            </button>

            {/* View Profile */}
            <SheetClose asChild>
              <Link
                to={`/profile/${user?.id}`}
                className="text-base font-medium text-foreground hover:text-green-500 transition-colors"
              >
                Ver Perfil
              </Link>
            </SheetClose>

            {/* Logout */}
            <button
              className="text-left text-base font-medium text-destructive hover:opacity-80 transition-opacity"
              onClick={() => { setSheetOpen(false); handleLogout() }}
            >
              Sair
            </button>
          </SheetContent>
        </Sheet>
      </div>
    </nav>
  )
}
