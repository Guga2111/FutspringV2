import { useMemo, useState } from "react"
import { HugeiconsIcon } from "@hugeicons/react"
import { ShuffleIcon } from "@hugeicons/core-free-icons"
import { TeamColorDot } from "@/components/daily/teams/TeamColorDot"
import { Button } from "@/components/ui/button"
import { StarCount } from "@/components/landing/StarCount"
import { DRAW_PLAYERS } from "@/components/landing/content"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { drawTeams } from "@/utils/landingTeamDraw"

const TEAMS = [
  { name: "Time Azul", color: "#1D6FE0" },
  { name: "Time Laranja", color: "#F07A2B" },
]

// the first draw is fixed (no shuffle), so the server render and the screenshots match
const firstDraw = () => 0

// The page's live thing: ten sample players and the app's own balancing rule
export function TeamDrawSection() {
  const ref = useReveal<HTMLElement>()
  const [round, setRound] = useState(0)
  const teams = useMemo(() => drawTeams(DRAW_PLAYERS, TEAMS.length, round === 0 ? firstDraw : Math.random), [round])

  return (
    <section
      id="sorteio"
      ref={ref}
      aria-labelledby="sorteio-title"
      className="grid grid-cols-1 gap-12 bg-background px-4 py-24 md:grid-cols-12 md:px-8 md:py-32"
    >
      <div className="md:col-span-5">
        <h2 id="sorteio-title" data-reveal className="font-display text-chapter font-extrabold uppercase">
          Times justos,
          <br />
          sem discussão
        </h2>
        <p data-reveal style={revealAt(1)} className="mt-6 max-w-[38ch] text-lg leading-relaxed">
          Cada jogador tem de 1 a 5 estrelas na pelada. O sorteio embaralha quem tem as mesmas estrelas e
          entrega times com a mesma força, do mais forte para o mais fraco. Teste com a galera ao lado.
        </p>
        <ul data-reveal style={revealAt(2)} className="mt-8 flex max-w-[28rem] flex-wrap gap-2">
          {DRAW_PLAYERS.map((player) => (
            <li key={player.id} className="flex items-center gap-2 rounded-full bg-card-elevated px-3 py-1.5 text-body">
              {player.name}
              <StarCount stars={player.stars} className="text-body-sm font-semibold" />
            </li>
          ))}
        </ul>
      </div>

      <div data-reveal style={revealAt(1)} className="md:col-span-6 md:col-start-7">
        <div data-ui className="rounded-hero bg-card-elevated p-4 md:p-6">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {TEAMS.map((team, t) => (
              <div key={team.name} className="rounded-tile bg-card p-4">
                <div className="flex items-center justify-between gap-2">
                  <h3 className="flex items-center gap-2 text-title">
                    <TeamColorDot color={team.color} className="size-3" />
                    {team.name}
                  </h3>
                  <StarCount stars={teams[t].stars} className="font-display text-score font-bold" iconClassName="size-5" />
                </div>
                <ul key={round} className="mt-3 space-y-1">
                  {teams[t].players.map((player, p) => (
                    <li
                      key={player.id}
                      style={{ animationDelay: `${p * 50}ms` }}
                      className="flex items-center justify-between rounded-md px-2 py-1.5 text-base animate-in fade-in slide-in-from-bottom-2 fill-mode-both"
                    >
                      {player.name}
                      <StarCount stars={player.stars} className="text-body text-muted-foreground" />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <Button
            variant="brand"
            size="lg"
            onClick={() => setRound((r) => r + 1)}
            className="mt-4 h-12 w-full text-base font-semibold transition-transform active:scale-[0.98]"
          >
            <HugeiconsIcon icon={ShuffleIcon} className="size-5" />
            {round === 0 ? "Sortear times" : "Sortear de novo"}
          </Button>
          <p aria-live="polite" className="sr-only">
            {TEAMS.map((team, t) => `${team.name}: ${teams[t].stars} estrelas`).join(", ")}
          </p>
        </div>
      </div>
    </section>
  )
}
