import { Minus, Plus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

type StepperSize = "lg" | "md" | "sm"

// lg: mobile score (40 px buttons, 28 px number), md: desktop score (36 / 24), sm: player goals/assists (24 / 14)
const sizes: Record<StepperSize, { button: string; value: string; gap: string }> = {
  lg: { button: "size-10 [&_svg]:size-[18px]", value: "min-w-7 text-[28px]", gap: "gap-2" },
  md: { button: "size-9 [&_svg]:size-[18px]", value: "min-w-7 text-2xl", gap: "gap-2.5" },
  sm: { button: "size-6 [&_svg]:size-3.5", value: "min-w-[18px] text-sm", gap: "gap-1.5" },
}

interface ScoreStepperProps {
  value: number
  onChange: (value: number) => void
  // e.g. "gols de Leal" -> "Diminuir gols de Leal"
  label: string
  size?: StepperSize
}

export function ScoreStepper({ value, onChange, label, size = "md" }: ScoreStepperProps) {
  const s = sizes[size]
  return (
    <div className={cn("flex items-center", s.gap)}>
      <Button
        type="button"
        variant="outline"
        size="icon"
        aria-label={`Diminuir ${label}`}
        disabled={value <= 0}
        onClick={() => onChange(Math.max(0, value - 1))}
        className={cn("shrink-0 bg-transparent text-secondary-foreground hover:bg-secondary", s.button)}
      >
        <Minus />
      </Button>
      <span className={cn("text-center font-bold tabular-nums", s.value)} aria-live="polite">
        {value}
      </span>
      <Button
        type="button"
        variant="outline"
        size="icon"
        aria-label={`Aumentar ${label}`}
        onClick={() => onChange(value + 1)}
        className={cn("shrink-0 bg-transparent text-secondary-foreground hover:bg-secondary", s.button)}
      >
        <Plus />
      </Button>
    </div>
  )
}
