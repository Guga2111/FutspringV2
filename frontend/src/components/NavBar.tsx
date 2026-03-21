import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Trophy, Sun, Moon } from 'lucide-react'
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

export default function NavBar() {
  const { logout, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [isDark, setIsDark] = useState(() =>
    document.documentElement.classList.contains('dark')
  )

  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.classList.contains('dark'))
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  function handleToggleDark() {
    document.documentElement.classList.toggle('dark')
    setIsDark(document.documentElement.classList.contains('dark'))
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
        <Trophy className="h-5 w-5 text-green-500" />
        FutSpring
      </Link>

      {/* Center: nav links (desktop only) */}
      <div className="hidden sm:flex items-center gap-6">
        <Link
          to="/home"
          className={`text-sm font-medium transition-colors hover:text-green-500 ${
            isHomeActive
              ? 'text-green-600 border-b-2 border-green-500 pb-0.5'
              : 'text-muted-foreground'
          }`}
        >
          Home
        </Link>
      </div>

      {/* Right: dark mode toggle + avatar dropdown */}
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          aria-label="Toggle dark mode"
          onClick={handleToggleDark}
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className="rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="User menu"
            >
              <Avatar className="h-8 w-8 cursor-pointer">
                {user?.image && (
                  <AvatarImage src={user.image} alt={user.username} />
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
                View Profile
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="cursor-pointer text-destructive focus:text-destructive"
              onClick={handleLogout}
            >
              Logout
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </nav>
  )
}
