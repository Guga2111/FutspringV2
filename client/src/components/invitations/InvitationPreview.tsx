import { PeladaAvatar } from "@/components/PeladaAvatar"
import { dayOfWeekLabel } from "@/lib/constants"
import type { PeladaPreview } from "@/types/invitation"

export function InvitationPreview({ pelada }: { pelada: PeladaPreview }) {
  return (
    <div className="flex min-w-0 items-center gap-4">
      <PeladaAvatar name={pelada.name} image={pelada.image} className="size-16 rounded-tile text-xl" />
      <div className="min-w-0">
        <h3 className="break-words text-lg font-semibold">{pelada.name}</h3>
        <p className="text-sm text-muted-foreground">{dayOfWeekLabel(pelada.dayOfWeek)} às {pelada.timeOfDay}</p>
      </div>
    </div>
  )
}
