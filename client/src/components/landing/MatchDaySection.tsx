import { SessionFragment, type SessionStage } from "@/components/landing/SessionFragment"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { cn } from "@/lib/utils"

// "One card, four fields": the session card further along in each colour field. The order
// is the match day itself, so the clauses carry it instead of numbers.
const FIELDS: { stage: SessionStage; clause: string; field: string }[] = [
  { stage: "list", clause: "Primeiro, a lista enche sozinha.", field: "bg-brand-muted text-foreground" },
  { stage: "draw", clause: "Depois, o sorteio equilibra.", field: "bg-brand text-brand-foreground" },
  { stage: "live", clause: "Aí, o placar corre.", field: "bg-highlight text-highlight-foreground" },
  { stage: "final", clause: "No fim, tudo vira ranking.", field: "bg-night text-night-foreground" },
]

export function MatchDaySection() {
  const ref = useReveal<HTMLElement>()
  return (
    <section id="como-funciona" ref={ref} aria-labelledby="como-funciona-title" className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4">
      {FIELDS.map(({ stage, clause, field }, i) => (
        <div key={stage} className={cn("group relative flex flex-col px-4 py-10 md:min-h-[84svh] md:px-8", field)}>
          {i === 0 ? (
            <h2 id="como-funciona-title" data-reveal className="font-display text-chapter font-extrabold uppercase">
              Um dia de jogo
            </h2>
          ) : (
            <span aria-hidden className="hidden md:block md:h-[1em] md:text-chapter" />
          )}
          <div data-reveal style={revealAt(i)} className="flex flex-1 items-center justify-center py-10">
            <SessionFragment
              stage={stage}
              className="w-full max-w-[280px] transition-transform duration-150 group-hover:-translate-y-1.5"
            />
          </div>
          <p data-reveal style={revealAt(i + 1)} className="max-w-[16ch] text-heading font-semibold leading-tight md:text-heading-lg">
            {clause}
          </p>
        </div>
      ))}
    </section>
  )
}
