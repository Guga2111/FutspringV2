import { Fragment } from "react"
import { Link } from "react-router-dom"
import type { LegalBlock, LegalDocument } from "@/components/legal/content"
import { LEGAL_EFFECTIVE_DATE, LEGAL_VERSION } from "@/lib/legal"

const PLACEHOLDER = /(\[[^\]]+\])/

// a [PLACEHOLDER] still waiting for the owner's value stands out on the page
function Text({ children }: { children: string }) {
  return children.split(PLACEHOLDER).map((part, index) =>
    PLACEHOLDER.test(part) ? (
      <mark key={index} className="rounded bg-warning/20 px-1 text-foreground">
        {part}
      </mark>
    ) : (
      <Fragment key={index}>{part}</Fragment>
    ),
  )
}

function Block({ block }: { block: LegalBlock }) {
  if (typeof block === "string") {
    return (
      <p>
        <Text>{block}</Text>
      </p>
    )
  }
  return (
    <ul className="list-disc space-y-2 pl-5 marker:text-subtle-foreground">
      {block.map((item) => (
        <li key={item}>
          <Text>{item}</Text>
        </li>
      ))}
    </ul>
  )
}

interface LegalDocumentPageProps {
  document: LegalDocument
  // the other document, linked at the end
  other: { to: string; label: string }
}

// Public page of the terms of use or the privacy policy: logo, title, version, an index of anchored
// sections (so a section can be linked) and a reading column
export function LegalDocumentPage({ document, other }: LegalDocumentPageProps) {
  return (
    <div className="page-enter min-h-screen bg-background text-foreground">
      <header className="mx-auto w-full max-w-[720px] px-4 py-8 md:px-8">
        <Link to="/" className="inline-flex items-center gap-2">
          <img src="/logo-64.webp" alt="" width={32} height={32} className="size-8 rounded-full object-cover" />
          <span className="text-xl font-bold text-brand-text">Futspring</span>
        </Link>
      </header>

      <main className="mx-auto flex w-full max-w-[720px] flex-col gap-10 px-4 pb-16 md:px-8">
        <div className="flex flex-col gap-3">
          <h1 className="font-display text-display font-bold leading-none md:text-display-lg">{document.title}</h1>
          <p className="text-body-sm text-muted-foreground">
            Vigente desde {LEGAL_EFFECTIVE_DATE} · versão {LEGAL_VERSION}
          </p>
          <p className="text-body-lg text-muted-foreground">
            <Text>{document.intro}</Text>
          </p>
        </div>

        <nav aria-label="Nesta página" className="rounded-tile border border-border bg-card p-4 md:p-5">
          <p className="mb-3 text-overline font-semibold uppercase tracking-wide text-faint-foreground">Nesta página</p>
          <ol className="grid list-decimal gap-x-6 gap-y-1.5 pl-5 text-body marker:text-subtle-foreground md:grid-cols-2">
            {document.sections.map((section) => (
              <li key={section.id}>
                <a href={`#${section.id}`} className="text-brand-text underline-offset-4 hover:underline">
                  {section.title}
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <div className="flex flex-col gap-8">
          {document.sections.map((section, index) => (
            <section key={section.id} id={section.id} className="flex scroll-mt-6 flex-col gap-3">
              <h2 className="text-title font-semibold">
                {index + 1}. {section.title}
              </h2>
              <div className="flex flex-col gap-3 text-body-lg leading-relaxed text-muted-foreground">
                {section.blocks.map((block) => (
                  <Block key={typeof block === "string" ? block : block.join("|")} block={block} />
                ))}
              </div>
            </section>
          ))}
        </div>

        <footer className="flex flex-wrap gap-x-6 gap-y-2 border-t border-border pt-6 text-body">
          <Link to={other.to} className="text-brand-text underline-offset-4 hover:underline">
            {other.label}
          </Link>
          <Link to="/" className="text-brand-text underline-offset-4 hover:underline">
            Voltar ao início
          </Link>
        </footer>
      </main>
    </div>
  )
}
