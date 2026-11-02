import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { buttonVariants } from "@/components/ui/button-variants"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { cn } from "@/lib/utils"

// "First step, already open": the closing line beside the app's own create-pelada sheet
export function ClosingSection() {
  const ref = useReveal<HTMLElement>()
  return (
    <section
      ref={ref}
      aria-labelledby="comecar-title"
      className="grid min-h-[84svh] grid-cols-1 content-between gap-12 bg-brand px-4 py-20 text-brand-foreground md:px-8 lg:grid-cols-12 lg:content-center"
    >
      <div className="lg:col-span-5 lg:self-end">
        <h2 id="comecar-title" data-reveal className="font-display text-poster font-extrabold uppercase">
          Monte a sua
          <br />
          pelada
        </h2>
        <p data-reveal style={revealAt(1)} className="mt-6 max-w-[36ch] text-lg leading-relaxed">
          Crie a conta, dê um nome à pelada, escolha o dia e mande o convite para a galera. Leva um minuto.
        </p>
        <Button
          data-reveal
          style={revealAt(2)}
          asChild
          size="lg"
          className="mt-8 h-12 px-8 text-base font-semibold transition-transform active:scale-[0.97]"
        >
          <Link to="/auth?tab=register">Criar minha pelada</Link>
        </Button>
      </div>

      <div data-reveal style={revealAt(1)} className="lg:col-span-5 lg:col-start-8">
        <div
          data-ui
          role="img"
          aria-label="Formulário de nova pelada: nome Pelada de Quinta, quinta-feira às 20:00, 2 times de 5"
          className="mx-auto w-full max-w-[440px] rounded-hero bg-card p-6 text-card-foreground shadow-dialog"
        >
          <p className="text-heading font-semibold">Nova pelada</p>
          <p className="mt-1 text-body text-muted-foreground">Você vira o admin e pode convidar a galera depois.</p>
          <FakeField label="Nome" value="Pelada de Quinta" />
          <div className="grid grid-cols-2 gap-3">
            <FakeField label="Dia" value="Quinta-feira" />
            <FakeField label="Horário" value="20:00" />
          </div>
          <p className="mt-4 text-body font-medium">Times</p>
          <div className="mt-2 grid grid-cols-2 gap-1 rounded-full bg-card-elevated p-1 text-body">
            <span className="rounded-full bg-primary py-2 text-center font-medium text-primary-foreground">2 times de 5</span>
            <span className="py-2 text-center text-muted-foreground">3 times de 5</span>
          </div>
          <span className={cn(buttonVariants({ variant: "brand" }), "mt-6 h-11 w-full")}>Criar pelada</span>
        </div>
      </div>
    </section>
  )
}

function FakeField({ label, value }: { label: string; value: string }) {
  return (
    <div className="mt-4">
      <p className="text-body font-medium">{label}</p>
      <p className="mt-2 flex h-10 items-center rounded-md border border-input px-3 text-body">{value}</p>
    </div>
  )
}
