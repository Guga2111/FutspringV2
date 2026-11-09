import { HugeiconsIcon } from "@hugeicons/react"
import { Search01Icon } from "@hugeicons/core-free-icons"
import { Input } from "@/components/ui/input"

// The search box of the panel's lists (the request is debounced by the page)
export function PlatformSearch({ value, onChange, label }: { value: string; onChange: (value: string) => void; label: string }) {
  return (
    <div className="relative w-full md:max-w-[320px]">
      <HugeiconsIcon
        icon={Search01Icon}
        aria-hidden
        className="pointer-events-none absolute left-3 top-1/2 size-[15px] -translate-y-1/2 text-subtle-foreground"
      />
      <Input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={label}
        aria-label={label}
        className="h-[38px] rounded-full bg-card pl-[34px] pr-3.5"
      />
    </div>
  )
}
