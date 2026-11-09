import { SettingsCard } from "@/components/SettingsCard"
import { FieldLabel } from "@/components/ui/field"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { useTheme, type ThemePreference } from "@/hooks/useTheme"
import { cn } from "@/lib/utils"

type PreviewTheme = "light" | "dark"

const OPTIONS: Record<ThemePreference, { label: string; halves: PreviewTheme[] }> = {
  light: { label: "Claro", halves: ["light"] },
  dark: { label: "Escuro", halves: ["dark"] },
  // the OS decides: half of each
  system: { label: "Sistema", halves: ["light", "dark"] },
}
const PREFERENCES = Object.keys(OPTIONS) as ThemePreference[]

// the miniature keeps its own colors on a page of either theme (the --preview-* tokens)
const PREVIEW: Record<PreviewTheme, { page: string; side: string; card: string; line: string; border: string }> = {
  light: { page: "bg-preview-light-page", side: "bg-preview-light-line/60", card: "bg-preview-light-card", line: "bg-preview-light-line", border: "border-preview-light-line" },
  dark: { page: "bg-preview-dark-page", side: "bg-preview-dark-card", card: "bg-preview-dark-card", line: "bg-preview-dark-line", border: "border-preview-dark-line" },
}

// Applied at once, no footer: the theme is a per-device preference (localStorage.theme)
export function AppearanceCard() {
  const { preference, setPreference } = useTheme()
  return (
    <SettingsCard id="aparencia" title="Aparência" description="Vale só para este dispositivo.">
      <RadioGroup
        value={preference}
        onValueChange={(value) => setPreference(value as ThemePreference)}
        aria-label="Tema"
        className="grid grid-cols-3 gap-2 md:gap-3"
      >
        {PREFERENCES.map((value) => {
          const id = `theme-${value}`
          return (
            <FieldLabel
              key={value}
              htmlFor={id}
              className="group flex w-full min-w-0 cursor-pointer flex-col overflow-hidden rounded-tile border font-normal transition-colors hover:border-input has-[[data-state=checked]]:border-brand has-[[data-state=checked]]:ring-1 has-[[data-state=checked]]:ring-brand"
            >
              <span className="flex h-20 md:h-24" aria-hidden="true">
                {OPTIONS[value].halves.map((half) => (
                  <ThemeMiniature key={half} theme={half} />
                ))}
              </span>
              <span className="flex items-center justify-between gap-2 border-t px-3 py-2.5 group-has-[[data-state=checked]]:bg-muted md:px-4 md:py-3">
                <span className="text-sm font-medium">{OPTIONS[value].label}</span>
                <RadioGroupItem value={value} id={id} />
              </span>
            </FieldLabel>
          )
        })}
      </RadioGroup>
    </SettingsCard>
  )
}

// A screen in miniature: the sidebar, a title bar and a card
function ThemeMiniature({ theme }: { theme: PreviewTheme }) {
  const colors = PREVIEW[theme]
  return (
    <span className={cn("flex min-w-0 flex-1 gap-1.5 p-2 md:gap-2 md:p-2.5", colors.page)}>
      <span className={cn("w-1/4 shrink-0 rounded-md", colors.side)} />
      <span className="flex min-w-0 flex-1 flex-col gap-1.5 md:gap-2">
        <span className={cn("h-1.5 w-3/4 rounded-full md:h-2", colors.line)} />
        <span className={cn("flex-1 rounded-md border", colors.card, colors.border)} />
      </span>
    </span>
  )
}
