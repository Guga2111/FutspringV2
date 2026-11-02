import { Link } from "react-router-dom"
import { Button } from "@/components/ui/button"
import { SessionFragment } from "@/components/landing/SessionFragment"

const NAV = [
  { href: "#como-funciona", label: "Como funciona" },
  { href: "#sorteio", label: "Sorteio" },
  { href: "#perguntas", label: "Perguntas" },
]

// First chapter: the headline on the bare ground and the pitch's centre circle, at a scale
// the viewport crops, opening from the right (.landing-shutter), with the session card on it
export function LandingHero() {
  return (
    <header className="relative isolate flex min-h-[100svh] flex-col overflow-hidden bg-background">
      <div
        aria-hidden
        className="landing-shutter absolute left-1/2 top-[56svh] -z-10 size-[130vw] -translate-x-1/2 md:left-[calc(78%-60vh)] md:top-[calc(55%-60vh)] md:size-[120vh] md:translate-x-0"
      >
        <div className="size-full rounded-full bg-brand" />
      </div>

      <nav aria-label="Principal" className="flex items-center justify-between gap-4 px-4 py-5 md:px-8">
        <Link to="/" className="font-display text-heading font-extrabold uppercase md:text-heading-lg">
          Futspring
        </Link>
        <div className="flex items-center gap-6">
          <ul className="hidden items-center gap-6 md:flex">
            {NAV.map((item) => (
              <li key={item.href}>
                <a href={item.href} className="text-base font-medium underline-offset-4 hover:underline">
                  {item.label}
                </a>
              </li>
            ))}
          </ul>
          <Link to="/auth" className="text-base font-semibold underline-offset-4 hover:underline">
            Entrar
          </Link>
        </div>
      </nav>

      <div className="grid flex-1 grid-cols-1 content-start gap-10 px-4 pb-10 pt-[8svh] md:grid-cols-12 md:content-center md:px-8 md:pt-0">
        <div className="md:col-span-6">
          <h1 className="landing-rise font-display text-poster font-extrabold uppercase">
            Sua pelada,
            <br />
            organizada.
          </h1>
          <p className="landing-rise mt-8 max-w-[34rem] text-lg leading-relaxed [animation-delay:120ms] md:text-xl">
            Lista de presença, sorteio de times, placar e ranking num lugar só, sem copiar e colar lista no
            grupo.
          </p>
          <div className="landing-rise mt-8 flex flex-wrap items-center gap-x-6 gap-y-3 [animation-delay:200ms]">
            <Button asChild variant="brand" size="lg" className="h-12 px-8 text-base font-semibold transition-transform active:scale-[0.97]">
              <Link to="/auth?tab=register">Criar minha pelada</Link>
            </Button>
            <Link to="/auth" className="text-base font-medium underline-offset-4 hover:underline">
              Já tenho conta
            </Link>
          </div>
        </div>

        <div className="flex items-start justify-center md:col-span-5 md:col-start-8 md:items-center md:justify-start">
          <SessionFragment stage="list" className="landing-rise w-full max-w-[340px] [animation-delay:500ms]" />
        </div>
      </div>
    </header>
  )
}
