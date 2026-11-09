import { LegalDocumentPage } from "@/components/legal/LegalDocumentPage"
import { TERMS } from "@/components/legal/content"

export default function TermsPage() {
  return <LegalDocumentPage document={TERMS} other={{ to: "/privacidade", label: "Política de privacidade" }} />
}
