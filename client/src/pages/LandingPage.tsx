import { Navigate } from "react-router-dom"
import { ClosingSection } from "@/components/landing/ClosingSection"
import { FaqSection } from "@/components/landing/FaqSection"
import { GallerySection } from "@/components/landing/GallerySection"
import { LandingFooter } from "@/components/landing/LandingFooter"
import { LandingHero } from "@/components/landing/LandingHero"
import { MatchDaySection } from "@/components/landing/MatchDaySection"
import { ShowcaseSection } from "@/components/landing/ShowcaseSection"
import { TeamDrawSection } from "@/components/landing/TeamDrawSection"
import { useAuth } from "@/hooks/useAuth"

// Colour chapters: each section is a full-bleed field that follows a match day
// (list → draw → score → ranking), then the management tools, the champions, the FAQ
export default function LandingPage() {
  const { token } = useAuth()

  if (token) {
    return <Navigate to="/home" replace />
  }

  return (
    <div id="top" className="page-enter">
      <LandingHero />
      <main>
        <MatchDaySection />
        <TeamDrawSection />
        <ShowcaseSection />
        <GallerySection />
        <FaqSection />
        <ClosingSection />
      </main>
      <LandingFooter />
    </div>
  )
}
