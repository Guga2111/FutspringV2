import { useState } from "react"
import { ptBR } from "date-fns/locale"
import { HugeiconsIcon } from "@hugeicons/react"
import { Calendar03Icon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { useIsMobile } from "@/hooks/useIsMobile"
import { formatRangeLabel, type DateRange } from "@/utils/dates"

interface DateRangeFilterProps {
  // undefined = the whole period
  value: DateRange | undefined
  onChange: (range: DateRange | undefined) => void
  // month shown when nothing is picked
  defaultMonth?: Date
}

// Period filter: a button with the range label that opens a range calendar (two months from md, one below)
export function DateRangeFilter({ value, onChange, defaultMonth }: DateRangeFilterProps) {
  const isMobile = useIsMobile()
  const [open, setOpen] = useState(false)
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" className="h-[34px] gap-1.5 bg-transparent px-3.5 tabular-nums hover:bg-accent">
          <HugeiconsIcon icon={Calendar03Icon} className="size-3.5" />
          {formatRangeLabel(value)}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="end">
        <Calendar
          mode="range"
          locale={ptBR}
          numberOfMonths={isMobile ? 1 : 2}
          selected={value?.from ? { from: value.from, to: value.to } : undefined}
          onSelect={(next) => onChange(next?.from ? { from: next.from, to: next.to } : undefined)}
          defaultMonth={value?.from ?? defaultMonth}
        />
        <div className="flex justify-end gap-2 border-t px-3 py-2">
          <Button variant="ghost" size="sm" disabled={!value?.from} onClick={() => onChange(undefined)}>
            Limpar
          </Button>
          <Button variant="outline" size="sm" onClick={() => setOpen(false)}>
            Pronto
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
