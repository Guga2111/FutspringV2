import { useMemo, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { Tick02Icon, UnfoldMoreIcon } from "@hugeicons/core-free-icons"
import { Button } from "@/components/ui/button"
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"

interface TimeZonePickerProps {
  id: string
  value: string
  onChange: (timeZone: string) => void
  invalid?: boolean
}

// Searchable IANA zone list (shadcn combobox: Popover + Command) from the browser's own list
export function TimeZonePicker({ id, value, onChange, invalid }: TimeZonePickerProps) {
  const [open, setOpen] = useState(false)
  const zones = useMemo(() => Intl.supportedValuesOf("timeZone"), [])
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          aria-invalid={invalid}
          className="w-full justify-between rounded-md font-normal"
        >
          <span className="truncate">{value.replaceAll("_", " ")}</span>
          <HugeiconsIcon icon={UnfoldMoreIcon} className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command>
          <CommandInput placeholder="Buscar cidade" />
          <CommandList>
            <CommandEmpty>Nenhum fuso encontrado.</CommandEmpty>
            <CommandGroup>
              {zones.map((zone) => (
                <CommandItem
                  key={zone}
                  value={zone}
                  onSelect={() => {
                    onChange(zone)
                    setOpen(false)
                  }}
                >
                  <HugeiconsIcon icon={Tick02Icon} className={cn("mr-2 size-4", zone === value ? "opacity-100" : "opacity-0")} />
                  {zone.replaceAll("_", " ")}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
