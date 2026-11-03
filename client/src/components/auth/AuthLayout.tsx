import type { ReactNode } from "react"
import { Link } from "react-router-dom"

// Shell of the public auth screens (login/register, forgot and reset password): logo, centered content and,
// from lg up, a side column: the photo, or `aside` on a card surface (the app download panel of the login)
export function AuthLayout({ children, aside }: { children: ReactNode; aside?: ReactNode }) {
  return (
    <div className="page-enter flex min-h-screen bg-background">
      <div className="flex flex-1 flex-col px-4 py-8 md:px-8">
        <Link to="/" className="flex items-center gap-2 self-start">
          <img src="/logo-64.webp" alt="" width={32} height={32} className="size-8 rounded-full object-cover" />
          <span className="text-xl font-bold text-brand-text">Futspring</span>
        </Link>
        <div className="flex flex-1 flex-col items-center justify-center gap-6">{children}</div>
      </div>
      {aside ? (
        <div className="relative hidden flex-1 items-center justify-center py-12 pl-24 pr-8 lg:flex">
          {/* the card surface with a diagonal left edge (wider at the bottom) */}
          <div aria-hidden className="absolute inset-0 bg-card [clip-path:polygon(18%_0,100%_0,100%_100%,0_100%)]" />
          <div className="relative flex w-full justify-center">{aside}</div>
        </div>
      ) : (
        <div className="relative hidden flex-1 overflow-hidden lg:block">
          <img src="/pele.jpg" alt="" className="absolute inset-0 size-full object-cover" />
        </div>
      )}
    </div>
  )
}
