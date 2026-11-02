import { useMemo } from "react"
import { NextSessionCard } from "@/components/pelada/NextSessionCard"
import { SessionHistoryList } from "@/components/pelada/SessionHistoryList"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"
import { Skeleton } from "@/components/ui/skeleton"
import { pickNextSession } from "@/utils/sessions"

export default function PeladaSessionsPage() {
  const w = usePeladaWorkspace()
  const nextSession = useMemo(() => pickNextSession(w.dailies), [w.dailies])
  const otherSessions = useMemo(() => w.dailies.filter((d) => d.id !== nextSession?.id), [w.dailies, nextSession])

  return (
    <div className="flex flex-col gap-5">
      {w.dailiesLoading ? (
        <Skeleton className="h-[116px] w-full rounded-tile" />
      ) : (
        <NextSessionCard
          daily={nextSession}
          timeZone={w.pelada.timeZone}
          isAdmin={w.isAdmin}
          attendancePending={w.attendancePending}
          onToggleAttendance={w.toggleAttendance}
        />
      )}
      <SessionHistoryList
        dailies={otherSessions}
        isLoading={w.dailiesLoading}
        isAdmin={w.isAdmin}
        onOpenCreate={w.openCreateSession}
      />
    </div>
  )
}
