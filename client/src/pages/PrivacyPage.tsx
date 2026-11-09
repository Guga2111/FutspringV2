import { LegalDocumentPage } from "@/components/legal/LegalDocumentPage"
import { PRIVACY } from "@/components/legal/content"

export default function PrivacyPage() {
  return <LegalDocumentPage document={PRIVACY} other={{ to: "/termos", label: "Termos de uso" }} />
}
