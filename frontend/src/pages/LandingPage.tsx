import { Navigate, Link } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

export default function LandingPage() {
  const { token } = useAuth()

  if (token) {
    return <Navigate to="/home" replace />
  }

  return (
    <div className="page-enter min-h-screen bg-gradient-to-br from-green-600 to-emerald-800 flex items-center">
      <div className="container mx-auto px-6 py-16 flex items-center justify-between gap-12">
        {/* Left: text + CTAs */}
        <div className="flex-1 max-w-xl">
          <h1 className="text-4xl md:text-6xl font-bold text-white leading-tight">
            Your football crew, organized.
          </h1>
          <p className="mt-4 text-lg text-green-100">
            Schedule peladas, auto-balance teams, track stats, and chat with your squad — all in one place.
          </p>
          <div className="mt-8 flex flex-wrap gap-4">
            <Link
              to="/auth?tab=register"
              className="inline-flex items-center justify-center px-6 py-3 rounded-md font-semibold bg-white text-green-700 hover:bg-green-50 transition-colors duration-150"
            >
              Get Started
            </Link>
            <Link
              to="/auth"
              className="inline-flex items-center justify-center px-6 py-3 rounded-md font-semibold border-2 border-white text-white hover:bg-white/10 transition-colors duration-150"
            >
              Sign In
            </Link>
          </div>
        </div>

        {/* Right: decorative element — desktop only */}
        <div className="hidden md:flex flex-1 items-center justify-center">
          <span className="text-[160px] select-none" role="img" aria-label="Football">⚽</span>
        </div>
      </div>
    </div>
  )
}
