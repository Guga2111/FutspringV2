import { MembershipBadge } from "@/components/MembershipBadge"
import { PlayerAvatar } from "@/components/PlayerAvatar"
import { ATTENDANCE } from "@/components/landing/content"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { buttonVariants } from "@/components/ui/button-variants"
import { cn } from "@/lib/utils"

// "Two objects, two fields": the attendance list and the Pix charge, each a product
// fragment cut by the top of its panel, with the name and a fact along the bottom
export function ShowcaseSection() {
  const ref = useReveal<HTMLElement>()
  return (
    <section ref={ref} aria-labelledby="gestao-title" className="grid grid-cols-1 md:grid-cols-2">
      <div className="relative flex flex-col overflow-hidden bg-card-elevated text-foreground md:min-h-[92svh]">
        <h2 id="gestao-title" data-reveal className="relative z-10 px-4 pt-8 font-display text-chapter font-extrabold uppercase md:px-8">
          Quem vai,
          <br />
          quem pagou
        </h2>
        <div data-reveal style={revealAt(1)} className="flex flex-1 items-start justify-end pl-12 pt-8 md:pl-[22%]">
          <AttendanceFragment />
        </div>
        <PanelFoot name="Lista de presença" fact="Mensalistas garantem a vaga. Diaristas confirmam a partir de 24 horas antes." />
      </div>

      <div className="relative flex flex-col overflow-hidden bg-night text-night-foreground md:min-h-[92svh]">
        <div data-reveal style={revealAt(2)} className="flex flex-1 items-start justify-center px-4 pt-12 md:pt-[22svh]">
          <PixFragment />
        </div>
        <PanelFoot name="Financeiro" fact="Cobranças com a chave Pix da pelada, quem já pagou e o caixa do grupo." />
      </div>
    </section>
  )
}

function PanelFoot({ name, fact }: { name: string; fact: string }) {
  return (
    <div data-reveal style={revealAt(3)} className="px-4 pb-8 pt-6 md:px-8">
      <p className="text-heading font-semibold">{name}</p>
      <p className="mt-1 max-w-[44ch] text-lg">{fact}</p>
    </div>
  )
}

function AttendanceFragment() {
  return (
    <div
      data-ui
      role="img"
      aria-label="Lista de presença com 5 confirmados em ordem de confirmação, mensalistas e diaristas"
      className="w-full max-w-[460px] rounded-l-hero border border-r-0 bg-card p-5 text-card-foreground"
    >
      <div className="flex items-center justify-between">
        <p className="text-title">Confirmados</p>
        <span className="text-body-sm text-muted-foreground">5 de 10</span>
      </div>
      <ul className="mt-4 space-y-1">
        {ATTENDANCE.map((row) => (
          <li key={row.id} className="flex items-center gap-3 rounded-md px-2 py-2 odd:bg-row-hover">
            <PlayerAvatar username={row.name} image={null} colorId={row.id} className="size-9 text-xs" />
            <span className="flex-1 text-body font-medium">{row.name}</span>
            <MembershipBadge isMensalista={row.isMensalista} />
            <span className="hidden w-20 text-right text-body-sm text-muted-foreground sm:block">{row.at}</span>
          </li>
        ))}
      </ul>
      <p className="mt-3 rounded-md bg-highlight-muted px-3 py-2 text-body-sm">
        <strong className="font-semibold">Márcio</strong> perdeu a vaga para um mensalista
      </p>
    </div>
  )
}

function PixFragment() {
  return (
    <div
      data-ui
      role="img"
      aria-label="Cobrança de novembro: R$ 60,00 por pessoa, 9 de 14 pagos, chave Pix da pelada"
      className="w-full max-w-[380px] rounded-hero border bg-card p-5 text-card-foreground"
    >
      <p className="text-title">Mensalidade de novembro</p>
      <p className="mt-1 text-body-sm text-muted-foreground">Vence 10/11 · R$ 60,00 por pessoa</p>
      <div className="mt-5 grid grid-cols-2 gap-2">
        <div className="rounded-tile bg-success-muted px-3 py-3">
          <p className="text-body-sm text-success-soft">Recebido</p>
          <p className="font-display text-score font-bold tabular-nums">R$ 540</p>
        </div>
        <div className="rounded-tile bg-card-elevated px-3 py-3">
          <p className="text-body-sm text-muted-foreground">A receber</p>
          <p className="font-display text-score font-bold tabular-nums">R$ 300</p>
        </div>
      </div>
      <div className="mt-4 rounded-tile bg-card-elevated px-3 py-3">
        <p className="text-body-sm text-muted-foreground">Chave Pix</p>
        <p className="mt-0.5 text-body font-medium">pelada.quinta@email.com</p>
      </div>
      <span className={cn(buttonVariants({ variant: "outline" }), "mt-4 w-full")}>Receber todos (5)</span>
    </div>
  )
}
