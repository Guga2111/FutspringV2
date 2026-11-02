import { PhotoSlot } from "@/components/landing/PhotoSlot"
import { CHAMPION_PHOTOS } from "@/components/landing/content"
import { revealAt, useReveal } from "@/components/landing/hooks/useReveal"
import { Carousel, CarouselContent, CarouselItem, CarouselNext, CarouselPrevious } from "@/components/ui/carousel"
import { cn } from "@/lib/utils"

// beside the headline, never on it: in a row under it on a phone, at the field's sides from md
const PRINTS = [
  { photo: CHAMPION_PHOTOS[4], className: "-rotate-[6deg] md:left-[3%] md:top-[12%]" },
  { photo: CHAMPION_PHOTOS[0], className: "rotate-[5deg] md:bottom-[12%] md:right-[3%]" },
]

// The achievement chapter (Cantaloupe): prints dropped on the headline, then every
// week's champion photo in a carousel
export function GallerySection() {
  const ref = useReveal<HTMLElement>()
  return (
    <section ref={ref} aria-labelledby="campeoes-title" className="overflow-hidden bg-highlight text-highlight-foreground">
      <div className="relative flex min-h-[78svh] flex-col items-center justify-center gap-10 px-4 py-20 md:min-h-[100svh] md:px-8">
        <h2 id="campeoes-title" data-reveal className="text-center font-display text-[19vw] font-extrabold uppercase leading-[0.85] md:text-[12vw]">
          Campeões
          <br />
          da semana
        </h2>
        <div className="flex gap-4 md:contents">
          {PRINTS.map(({ photo, className }, i) => (
            <figure
              key={photo.alt}
              data-reveal
              style={revealAt(i + 1)}
              className={cn(
                "aspect-[3/4] w-[40vw] bg-paper p-2 transition-transform duration-200 hover:rotate-0 md:absolute md:w-[16vw]",
                className,
              )}
            >
              <PhotoSlot photo={photo} width={900} height={1200} />
            </figure>
          ))}
        </div>
        <p className="max-w-[34ch] self-start text-base font-medium md:absolute md:bottom-8 md:left-8 md:text-lg">
          Cada sessão finalizada guarda a foto do time campeão, a tabela e os prêmios da noite.
        </p>
      </div>

      <Carousel opts={{ align: "start" }} className="px-4 pb-24 md:px-8 md:pb-32" aria-label="Fotos dos campeões">
        <div className="mb-6 flex items-end justify-between gap-4">
          <h3 className="text-heading font-semibold md:text-heading-lg">Toda semana tem foto nova</h3>
          <div className="flex gap-2">
            <CarouselPrevious className="static size-11 translate-y-0 border-0 bg-night text-night-foreground hover:bg-night/85 hover:text-night-foreground" />
            <CarouselNext className="static size-11 translate-y-0 border-0 bg-night text-night-foreground hover:bg-night/85 hover:text-night-foreground" />
          </div>
        </div>
        <CarouselContent>
          {CHAMPION_PHOTOS.map((photo) => (
            <CarouselItem key={photo.alt} className="basis-[78%] sm:basis-1/2 lg:basis-1/3">
              <figure>
                <div className="aspect-[4/3] overflow-hidden rounded-hero">
                  <PhotoSlot photo={photo} fit="contain" width={900} height={1200} />
                </div>
                <figcaption className="mt-3 text-base">
                  <span className="font-semibold">{photo.date}</span> · {photo.caption}
                </figcaption>
              </figure>
            </CarouselItem>
          ))}
        </CarouselContent>
      </Carousel>
    </section>
  )
}
