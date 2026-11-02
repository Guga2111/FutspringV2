import { Link } from "react-router-dom"
import { CHAMPION_PHOTOS } from "@/components/landing/content"

const SECTIONS = [
  { href: "#como-funciona", label: "Como funciona" },
  { href: "#sorteio", label: "Sorteio" },
  { href: "#perguntas", label: "Perguntas" },
]

// credits of the photos the owner added, when a photo carries one
const credits = CHAMPION_PHOTOS.flatMap((photo) => (photo.src && photo.credit ? [photo.credit] : []))

export function LandingFooter() {
  return (
    <footer className="overflow-hidden bg-night px-4 pt-20 text-night-foreground md:px-8 md:pt-28">
      <div className="grid grid-cols-1 gap-12 md:grid-cols-12">
        <ul className="space-y-2 md:col-span-5">
          <li>
            <Link to="/auth?tab=register" className="text-[26px] font-semibold underline-offset-8 hover:underline md:text-4xl">
              Criar conta
            </Link>
          </li>
          <li>
            <Link to="/auth" className="text-[26px] font-semibold underline-offset-8 hover:underline md:text-4xl">
              Entrar
            </Link>
          </li>
        </ul>
        <nav aria-label="Seções" className="md:col-span-5 md:col-start-8">
          <ul className="space-y-3">
            {SECTIONS.map((item) => (
              <li key={item.href}>
                <a href={item.href} className="text-base underline-offset-4 hover:underline">
                  {item.label}
                </a>
              </li>
            ))}
          </ul>
        </nav>
      </div>

      <div className="mt-20 flex flex-wrap justify-between gap-4 text-body-sm text-night-muted">
        <p>© {new Date().getFullYear()} Futspring. Feito para quem joga pelada com os amigos.</p>
        {credits.length > 0 && <p>Fotos: {credits.join(" · ")}</p>}
        <a href="#top" className="underline-offset-4 hover:text-night-foreground hover:underline">
          Voltar ao topo
        </a>
      </div>

      <p aria-hidden className="-mb-[0.12em] mt-10 select-none text-center font-display text-wordmark font-extrabold uppercase">
        Futspring
      </p>
    </footer>
  )
}
