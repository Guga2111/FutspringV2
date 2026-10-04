import { Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { greeting, pendingLine } from "@/utils/home"
import { formatLongDate } from "@/utils/sessions"
import { format } from "date-fns"

interface HomeBannerProps {
  username: string
  pendingCount: number
  onCreatePelada: () => void
}

// Greeting over the photo: text stays light in both themes (it sits on the darkened image)
export function HomeBanner({ username, pendingCount, onCreatePelada }: HomeBannerProps) {
  const now = new Date()
  return (
    <header className="relative min-h-[220px] overflow-hidden rounded-hero bg-muted md:min-h-[240px]">
      <img
        src="/ronaldo.jpg"
        alt=""
        className="absolute inset-0 size-full object-cover opacity-55 [filter:grayscale(1)_contrast(1.05)] [object-position:70%_30%] md:[object-position:right_30%]"
      />
      <div className="bg-hero-overlay absolute inset-0" />
      <div className="relative flex min-h-[220px] flex-wrap items-end justify-between gap-5 px-5 py-[22px] md:min-h-[240px] md:px-9 md:py-8">
        <div className="flex max-w-[560px] flex-col gap-2.5">
          <span className="bg-brand-translucent text-on-brand-muted flex items-center gap-1.5 self-start rounded-full border px-2.5 py-1 text-xs font-semibold">
            {formatLongDate(format(now, "yyyy-MM-dd"))}
          </span>
          <h1 className="text-[28px] font-extrabold leading-[1.1] tracking-[-0.015em] text-white md:text-[38px]">
            {greeting(now)}, {username}
          </h1>
          <span className="text-[15px] text-white/85">{pendingLine(pendingCount)}</span>
        </div>
        <Button variant="gradient" onClick={onCreatePelada} className="h-[42px] gap-2 px-5 font-semibold">
          <Plus className="size-[15px]" strokeWidth={2.2} />
          Nova pelada
        </Button>
      </div>
    </header>
  )
}
