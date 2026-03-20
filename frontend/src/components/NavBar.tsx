import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Button } from './ui/button'

export default function NavBar() {
  const { logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/auth', { replace: true })
  }

  return (
    <nav className="flex items-center justify-between px-6 py-3 border-b bg-white">
      <span className="font-bold text-lg">FutSpring</span>
      <Button variant="outline" size="sm" onClick={handleLogout}>
        Logout
      </Button>
    </nav>
  )
}
