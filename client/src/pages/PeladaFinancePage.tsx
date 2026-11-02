import { FinanceTab } from "@/components/finance/FinanceTab"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"

// The route is lazy, so the finance module still downloads only when it is opened
export default function PeladaFinancePage() {
  const w = usePeladaWorkspace()
  return (
    <FinanceTab
      key={w.pelada.id}
      peladaId={w.pelada.id}
      userId={w.currentUserId}
      members={w.pelada.members}
      dailies={w.dailies}
    />
  )
}
