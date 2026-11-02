import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
import { FAQ } from "@/components/landing/content"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"

// Sticky heading beside an accordion of filled tiles; the open tile takes the brand fill
export function FaqSection() {
  const ref = useReveal<HTMLElement>()
  return (
    <section
      id="perguntas"
      ref={ref}
      aria-labelledby="perguntas-title"
      className="grid grid-cols-1 gap-10 bg-background px-4 py-24 md:grid-cols-12 md:px-8 md:py-32"
    >
      <div className="md:col-span-4">
        <h2 id="perguntas-title" data-reveal className="font-display text-chapter font-extrabold uppercase md:sticky md:top-24">
          Perguntas
          <br />
          da galera
        </h2>
      </div>
      <Accordion data-reveal style={revealAt(1)} type="single" collapsible className="space-y-2 md:col-span-8">
        {FAQ.map((item) => (
          <AccordionItem
            key={item.id}
            value={item.id}
            className="rounded-tile border-0 bg-card-elevated px-5 transition-colors data-[state=open]:bg-brand data-[state=open]:text-brand-foreground md:px-6"
          >
            <AccordionTrigger className="min-h-[64px] gap-4 py-4 text-left text-lg font-medium hover:no-underline md:min-h-[72px] [&>svg]:size-5">
              {item.question}
            </AccordionTrigger>
            <AccordionContent className="max-w-[56ch] pb-6 text-base leading-relaxed md:text-lg">{item.answer}</AccordionContent>
          </AccordionItem>
        ))}
      </Accordion>
    </section>
  )
}
