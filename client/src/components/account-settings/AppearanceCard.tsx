import { HugeiconsIcon, type IconSvgElement } from "@hugeicons/react"
import { ComputerIcon, Moon02Icon, Sun03Icon } from "@hugeicons/core-free-icons"
import { SettingsCard } from "@/components/SettingsCard"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useTheme, type ThemePreference } from "@/hooks/useTheme"

const OPTIONS: Record<ThemePreference, { label: string; icon: IconSvgElement }> = {
  light: { label: "Claro", icon: Sun03Icon },
  dark: { label: "Escuro", icon: Moon02Icon },
  system: { label: "Sistema", icon: ComputerIcon },
}
const PREFERENCES = Object.keys(OPTIONS) as ThemePreference[]

// Applied at once, no footer: the theme is a per-device preference (localStorage.theme)
export function AppearanceCard() {
  const { preference, setPreference } = useTheme()
  return (
    <SettingsCard id="aparencia" title="Aparência" description="Vale só para este dispositivo.">
      <ToggleGroup
        type="single"
        variant="chip"
        size="chip"
        value={preference}
        onValueChange={(value) => value && setPreference(value as ThemePreference)}
        aria-label="Tema"
        className="flex-wrap justify-start gap-1.5"
      >
        {PREFERENCES.map((value) => (
          <ToggleGroupItem key={value} value={value} variant="chip" size="chip" className="gap-1.5">
            <HugeiconsIcon icon={OPTIONS[value].icon} className="size-4" />
            {OPTIONS[value].label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </SettingsCard>
  )
}
