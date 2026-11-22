import { useId, useState } from "react"
import { Link } from "react-router-dom"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Slider } from "@/components/ui/slider"
import { cn } from "@/lib/utils"
import { LARGE_GROUP_MEMBERS, TRIAL_DAYS, TRIAL_MEMBER_CAP, landingPricing } from "@/utils/subscription"

const MIN_MEMBERS = 10
const MAX_MEMBERS = 60
const START_MEMBERS = 18
const ROSTER = Array.from({ length: MAX_MEMBERS }, (_, i) => i + 1)

// "How many play?": the price depends on the pelada's size, so the visitor sets it. One dot per
// member, the slider, what that size means for the trial and the two plans' prices
export function PricingSection() {
  const ref = useReveal<HTMLElement>()
  const labelId = useId()
  const [members, setMembers] = useState(START_MEMBERS)
  const pricing = landingPricing(members)
  const band = pricing.large ? "large" : "standard"
  const count = members >= MAX_MEMBERS ? `${MAX_MEMBERS}+` : String(members)

  return (
    <section
      id="precos"
      ref={ref}
      aria-labelledby="precos-title"
      className="grid grid-cols-1 gap-14 bg-night px-4 py-24 text-night-foreground md:px-8 md:py-32 lg:grid-cols-12 lg:gap-8"
    >
      <div className="lg:col-span-5">
        <h2 id="precos-title" data-reveal className="font-display text-chapter font-extrabold uppercase">
          Um preço
          <br />
          por pelada
        </h2>
        <p data-reveal style={revealAt(1)} className="mt-6 max-w-[34ch] text-lg leading-relaxed md:text-xl">
          Quem cria a pelada assina. A galera joga sem pagar nada ao Futspring.
        </p>
        <ul data-reveal style={revealAt(2)} className="mt-8 flex max-w-[40ch] flex-col gap-3 text-base md:text-lg">
          <li>
            A primeira pelada da sua conta tem <strong className="font-semibold">{TRIAL_DAYS} dias grátis</strong>, com até{" "}
            {TRIAL_MEMBER_CAP} membros.
          </li>
          <li>Pago por Pix, sem cartão.</li>
        </ul>
        <Button
          data-reveal
          style={revealAt(3)}
          asChild
          variant="brand"
          size="lg"
          className="mt-10 h-12 px-8 text-base font-semibold transition-transform active:scale-[0.97]"
        >
          <Link to="/auth?tab=register">Começar teste grátis</Link>
        </Button>
      </div>

      <div data-reveal style={revealAt(1)} className="lg:col-span-6 lg:col-start-7 lg:self-center">
        <div className="flex items-end justify-between gap-4">
          <Label id={labelId} className="text-title font-semibold leading-snug md:text-heading">
            Quantos jogam na sua pelada?
          </Label>
          <p aria-hidden className="shrink-0 text-body text-night-muted">
            <span className="font-display text-score font-bold tabular-nums text-night-foreground md:text-score-lg">{count}</span>{" "}
            membros
          </p>
        </div>

        <div aria-hidden className="mt-5 grid grid-cols-[repeat(15,minmax(0,1fr))] gap-1.5 md:grid-cols-[repeat(20,minmax(0,1fr))]">
          {ROSTER.map((n) => (
            <span
              key={n}
              className={cn(
                "aspect-square rounded-full transition-colors duration-150",
                n <= members ? "bg-brand" : "bg-night-muted/35",
              )}
            />
          ))}
        </div>

        <Slider
          className="mt-6 py-2"
          min={MIN_MEMBERS}
          max={MAX_MEMBERS}
          step={1}
          value={[members]}
          onValueChange={([value]) => setMembers(value)}
          trackClassName="bg-night-muted/35"
          rangeClassName="bg-brand"
          thumbClassName="size-7 border-brand bg-night-foreground ring-offset-night"
          thumbProps={{
            "aria-labelledby": labelId,
            "aria-valuetext": `${count} membros`,
          }}
        />
        <div aria-hidden className="mt-2 flex justify-between text-body-sm text-night-muted">
          <span>{MIN_MEMBERS}</span>
          <span>{MAX_MEMBERS}+</span>
        </div>

        <p aria-live="polite" className="mt-5 min-h-[3.25rem] text-base md:text-lg">
          {pricing.fitsTrial
            ? "Cabe no teste grátis."
            : pricing.large
              ? `Acima de ${LARGE_GROUP_MEMBERS} membros vale o preço de pelada grande.`
              : `Acima de ${TRIAL_MEMBER_CAP} membros, a pelada precisa estar assinada.`}
        </p>

        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <PlanTile name="Mensal" price={pricing.monthly} unit="por mês" band={band} />
          <PlanTile
            name="Anual"
            price={pricing.annual}
            unit="por ano"
            band={band}
            detail={`≈ ${pricing.annualPerMonth} por mês`}
            badge={`${pricing.annualSaving} a menos que 12× o mensal`}
          />
        </div>
      </div>
    </section>
  )
}

interface PlanTileProps {
  name: string
  price: string
  unit: string
  // the price band: keyed so the numbers fade in again when it changes
  band: string
  detail?: string
  badge?: string
}

function PlanTile({ name, price, unit, band, detail, badge }: PlanTileProps) {
  return (
    <div className="flex flex-col rounded-hero bg-card p-5 text-card-foreground md:p-6">
      <p className="text-title font-semibold">{name}</p>
      <div key={band} className="mt-4 flex flex-1 flex-col animate-in fade-in duration-300">
        <p className="font-display text-score font-bold tabular-nums md:text-score-lg">{price}</p>
        <p className="text-body text-muted-foreground">{unit}</p>
        {detail && <p className="mt-3 text-body font-medium">{detail}</p>}
        {badge && (
          <Badge variant="status" className="mt-3 self-start whitespace-normal bg-brand-muted text-brand-text">
            {badge}
          </Badge>
        )}
      </div>
    </div>
  )
}
