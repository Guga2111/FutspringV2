import { Navigate, Link } from 'react-router-dom'
import { Calendar, Users, BarChart2, MessageSquare } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

const features = [
  {
    icon: Calendar,
    title: 'Schedule Sessions',
    description: 'Plan your peladas in advance and let everyone know when the next game is.',
  },
  {
    icon: Users,
    title: 'Auto-balance Teams',
    description: 'Automatically sort players into fair, balanced teams based on skill ratings.',
  },
  {
    icon: BarChart2,
    title: 'Track Stats',
    description: 'Keep score of goals, assists, and ratings so everyone knows who's on fire.',
  },
  {
    icon: MessageSquare,
    title: 'Group Chat',
    description: 'Stay in sync with your crew using the built-in pelada chat.',
  },
]

const steps = [
  { number: '1', title: 'Create a Pelada', description: 'Set up your group, add a banner, and invite your friends.' },
  { number: '2', title: 'Invite Friends', description: 'Share the link or search by username — get everyone on board.' },
  { number: '3', title: 'Play & Track Stats', description: 'Run sessions, sort teams, and record results after the game.' },
]

export default function LandingPage() {
  const { token } = useAuth()

  if (token) {
    return <Navigate to="/home" replace />
  }

  return (
    <div className="page-enter">
      {/* Hero */}
      <div className="min-h-screen bg-gradient-to-br from-green-600 to-emerald-800 flex items-center">
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

      {/* Features */}
      <section className="bg-green-50 dark:bg-green-950/20 py-20">
        <div className="container mx-auto px-6">
          <h2 className="text-3xl font-bold text-center mb-12">Everything your crew needs</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {features.map(({ icon: Icon, title, description }) => (
              <div
                key={title}
                className="rounded-xl border bg-background shadow-sm p-6 flex flex-col gap-3"
              >
                <div className="w-10 h-10 rounded-lg bg-green-100 dark:bg-green-900/40 flex items-center justify-center">
                  <Icon className="w-5 h-5 text-green-600 dark:text-green-400" />
                </div>
                <h3 className="font-semibold text-lg">{title}</h3>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="py-20">
        <div className="container mx-auto px-6">
          <h2 className="text-3xl font-bold text-center mb-12">How It Works</h2>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-8 max-w-3xl mx-auto">
            {steps.map(({ number, title, description }) => (
              <div key={number} className="flex flex-col items-center text-center gap-4">
                <div className="w-12 h-12 rounded-full bg-gradient-to-br from-green-500 to-emerald-600 flex items-center justify-center text-white font-bold text-xl">
                  {number}
                </div>
                <h3 className="font-semibold text-lg">{title}</h3>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-gray-900 text-gray-400 py-8">
        <div className="container mx-auto px-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-sm">FutSpring © 2025</p>
          <Link to="/auth" className="text-sm hover:text-white transition-colors duration-150">
            Sign In
          </Link>
        </div>
      </footer>
    </div>
  )
}
