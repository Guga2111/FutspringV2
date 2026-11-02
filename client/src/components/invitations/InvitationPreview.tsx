import { HugeiconsIcon } from "@hugeicons/react"
import { Calendar03Icon } from "@hugeicons/core-free-icons"
import { PeladaAvatar } from "@/components/PeladaAvatar"
import { dayOfWeekLabel } from "@/lib/constants"
import { cn } from "@/lib/utils"
import type { PeladaPreview } from "@/types/invitation"

// The pelada as an invitation shows it: photo or initials, name, weekday and time.
// "lg" is the page header of /invitations/:id and /join/:token; "md" the Home card.
export function InvitationPreview({ pelada, size = "md" }: { pelada: PeladaPreview; size?: "md" | "lg" }) {
  const large = size === "lg"
  return (
    <div className="flex min-w-0 items-center gap-4">
      <PeladaAvatar
        name={pelada.name}
        image={pelada.image}
        className={cn("shrink-0 rounded-tile", large ? "size-20 text-heading" : "size-14 text-title")}
      />
      <div className="flex min-w-0 flex-col gap-1">
        <p className={cn("break-words leading-tight", large ? "font-display text-heading font-bold md:text-heading-lg" : "text-title font-semibold")}>
          {pelada.name}
        </p>
        <p className="flex items-center gap-1.5 text-body text-muted-foreground">
          <HugeiconsIcon icon={Calendar03Icon} className="size-4 shrink-0" />
          {dayOfWeekLabel(pelada.dayOfWeek)} às {pelada.timeOfDay}
        </p>
      </div>
    </div>
  )
}
