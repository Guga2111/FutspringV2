import { Navigate, useLocation } from "react-router-dom"
import type { ReactNode } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/hooks/useAuth"

// The shell's outline while the boot refresh restores the session (the access token lives only in memory)
function SessionSkeleton() {
  return (
    <div className="flex min-h-svh bg-background" aria-busy="true" aria-label="Carregando sessão">
      <div className="hidden w-[264px] shrink-0 flex-col gap-3 border-r border-border p-4 md:flex">
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
      </div>
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-8">
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-32 w-full rounded-tile" />
        <Skeleton className="h-32 w-full rounded-tile" />
      </div>
    </div>
  )
}

export default function PrivateRoute({ children }: { children: ReactNode }) {
  const { user, restoring } = useAuth()
  const location = useLocation()

  if (restoring) return <SessionSkeleton />

  if (!user) {
    return <Navigate to="/auth" state={{ from: location }} replace />
  }

  return <>{children}</>
}
