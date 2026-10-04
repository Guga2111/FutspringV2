import { Bookmark, Calendar, Clock, MapPin, Pencil, Trash2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { dayOfWeekLabel } from "@/lib/constants"
import { cn, getFileUrl, getInitials, getPeladaGradient } from "@/lib/utils"
import type { PeladaDetail } from "@/types/pelada"

interface PeladaBannerProps {
  pelada: PeladaDetail
  isCurrentUserAdmin: boolean
  isCurrentUserCreator: boolean
  onEdit: () => void
  onDelete: () => void
}

// Text and buttons sit on the photo/gradient, so they stay white in both themes
const overlayButton = "size-[34px] rounded-full bg-black/35 text-white hover:bg-black/55 hover:text-white [&_svg]:size-[15px]"

export function PeladaBanner({ pelada, isCurrentUserAdmin, isCurrentUserCreator, onEdit, onDelete }: PeladaBannerProps) {
  const imageUrl = getFileUrl(pelada.image)
  return (
    <section
      className={cn(
        "relative flex min-h-[190px] flex-col justify-end overflow-hidden rounded-tile md:min-h-[230px]",
        !imageUrl && getPeladaGradient(pelada.name),
      )}
    >
      {imageUrl ? (
        <img src={imageUrl} alt="" className="absolute inset-0 size-full object-cover" style={{ objectPosition: "center 22%" }} />
      ) : (
        <span
          aria-hidden
          className="absolute inset-0 flex select-none items-center justify-center pb-10 text-[56px] font-extrabold tracking-[0.02em] text-white/55"
        >
          {getInitials(pelada.name)}
        </span>
      )}
      <div className="bg-banner-overlay absolute inset-0" />

      {(isCurrentUserAdmin || isCurrentUserCreator) && (
        <div className="absolute right-3 top-3 z-10 flex gap-2">
          {isCurrentUserAdmin && (
            <Button variant="ghost" size="icon" aria-label="Editar pelada" className={overlayButton} onClick={onEdit}>
              <Pencil />
            </Button>
          )}
          {isCurrentUserCreator && (
            <Button
              variant="ghost"
              size="icon"
              aria-label="Excluir pelada"
              className={cn(overlayButton, "hover:bg-destructive/80")}
              onClick={onDelete}
            >
              <Trash2 />
            </Button>
          )}
        </div>
      )}

      <div className="relative flex flex-col gap-2 px-[22px] py-5 text-white">
        <h1 className="text-2xl font-bold">{pelada.name}</h1>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-[13px] text-white/90 [&_svg]:size-3.5 [&_svg]:shrink-0">
          <span className="flex items-center gap-1.5">
            <Calendar />
            {dayOfWeekLabel(pelada.dayOfWeek)}
          </span>
          <span className="flex items-center gap-1.5">
            <Clock />
            {pelada.timeOfDay} · {pelada.duration}h
          </span>
          {pelada.address && (
            <span className="flex items-center gap-1.5">
              <MapPin />
              {pelada.address}
            </span>
          )}
          {pelada.reference && (
            <span className="flex items-center gap-1.5">
              <Bookmark />
              {pelada.reference}
            </span>
          )}
        </div>
      </div>
    </section>
  )
}
