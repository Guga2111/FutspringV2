import type { ReactNode } from "react"
import { Link } from "react-router-dom"

// Shell of the public auth screens (login/register, forgot and reset password): logo, centered content,
// photo on the right from lg up
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="page-enter flex min-h-screen bg-background">
      <div className="flex flex-1 flex-col px-4 py-8 md:px-8">
        <Link to="/" className="flex items-center gap-2 self-start">
          <img src="/gerrard.png" alt="" width={32} height={32} className="size-8 rounded-full object-cover shadow" />
          <span className="text-xl font-bold text-gradient-primary">Futspring</span>
        </Link>
        <div className="flex flex-1 flex-col items-center justify-center gap-6">{children}</div>
      </div>
      <div className="relative hidden flex-1 overflow-hidden lg:block">
        <img src="/pele.jpg" alt="" className="absolute inset-0 size-full object-cover" />
      </div>
    </div>
  )
}
