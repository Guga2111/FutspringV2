import { HugeiconsIcon } from "@hugeicons/react"
import { Bookmark01Icon, Calendar03Icon, Clock01Icon, Location01Icon, Settings01Icon } from "@hugeicons/core-free-icons"
import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { dayOfWeekLabel } from "@/lib/constants"
import { cn, getFileUrl, getInitials, getPeladaColor } from "@/lib/utils"
import type { PeladaDetail } from "@/types/pelada"

interface PeladaBannerProps {
  pelada: PeladaDetail
  isCurrentUserAdmin: boolean
}

// Text and buttons sit on the photo/gradient, so they stay white in both themes
const overlayButton = "size-[34px] rounded-full bg-black/35 text-white hover:bg-black/55 hover:text-white [&_svg]:size-[15px]"

export function PeladaBanner({ pelada, isCurrentUserAdmin }: PeladaBannerProps) {
  const imageUrl = getFileUrl(pelada.image)
  return (
    <section
      className={cn(
        "relative flex min-h-[190px] flex-col justify-end overflow-hidden rounded-tile md:min-h-[230px]",
        !imageUrl && getPeladaColor(pelada.name),
      )}
    >
      {imageUrl ? (
        <img src={imageUrl} alt="" className="absolute inset-0 size-full object-cover" style={{ objectPosition: "center 22%" }} />
      ) : (
        <span
          aria-hidden
          className="absolute inset-0 flex select-none items-center justify-center pb-10 font-display text-hero font-extrabold tracking-[0.02em] text-white/55"
        >
          {getInitials(pelada.name)}
        </span>
      )}
      <div className="bg-banner-overlay absolute inset-0" />

      {isCurrentUserAdmin && (
        <div className="absolute right-3 top-3 z-10">
          <Button variant="ghost" size="icon" asChild className={overlayButton}>
            <Link to={`/pelada/${pelada.id}/settings`} aria-label="Configurações da pelada">
              <HugeiconsIcon icon={Settings01Icon} />
            </Link>
          </Button>
        </div>
      )}

      <div className="relative flex flex-col gap-2 px-[22px] py-5 text-white">
        <h1 className="font-display text-heading-lg font-bold">{pelada.name}</h1>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-white/90 [&_svg]:size-3.5 [&_svg]:shrink-0">
          <span className="flex items-center gap-1.5">
            <HugeiconsIcon icon={Calendar03Icon} />
            {dayOfWeekLabel(pelada.dayOfWeek)}
          </span>
          <span className="flex items-center gap-1.5">
            <HugeiconsIcon icon={Clock01Icon} />
            {pelada.timeOfDay} · {pelada.duration}h
          </span>
          {pelada.address && (
            <span className="flex items-center gap-1.5">
              <HugeiconsIcon icon={Location01Icon} />
              {pelada.address}
            </span>
          )}
          {pelada.reference && (
            <span className="flex items-center gap-1.5">
              <HugeiconsIcon icon={Bookmark01Icon} />
              {pelada.reference}
            </span>
          )}
        </div>
      </div>
    </section>
  )
}
