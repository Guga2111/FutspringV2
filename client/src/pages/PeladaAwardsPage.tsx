import { AwardsTab } from "@/components/pelada/ranking/AwardsTab"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"

export default function PeladaAwardsPage() {
  const w = usePeladaWorkspace()
  return <AwardsTab awards={w.awards} isLoading={w.awardsLoading} />
}
