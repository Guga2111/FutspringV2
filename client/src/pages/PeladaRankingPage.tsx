import { useMemo, useState } from "react"
import { RankingCommandButton } from "@/components/pelada/RankingCommandButton"
import { RankingTable, type RankingCol } from "@/components/pelada/RankingTable"
import { usePeladaWorkspace } from "@/components/pelada/hooks/usePeladaWorkspace"

export default function PeladaRankingPage() {
  const w = usePeladaWorkspace()
  const [sort, setSort] = useState<{ col: RankingCol; dir: "asc" | "desc" }>({ col: "goals", dir: "desc" })

  const handleSort = (col: RankingCol) => {
    setSort((prev) => (prev.col === col ? { col, dir: prev.dir === "desc" ? "asc" : "desc" } : { col, dir: "desc" }))
  }

  const sorted = useMemo(
    () =>
      [...w.ranking].sort((a, b) => {
        const diff = b[sort.col] - a[sort.col]
        return sort.dir === "desc" ? diff : -diff
      }),
    [w.ranking, sort],
  )

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="text-sm text-muted-foreground">Top jogadores · {sorted.length} classificados</span>
        {/* its ⌘K / ⌘I / ⌘P shortcuts only listen while this page is open */}
        <RankingCommandButton
          peladaId={w.pelada.id}
          members={w.pelada.members}
          creatorId={w.pelada.creatorId}
          isAdmin={w.isAdmin}
          onCreateSession={w.openCreateSession}
          onInvitePlayer={w.openInvite}
          onRemovePlayer={w.confirmRemove}
          onOpenHistory={() => w.openHistory(null)}
        />
      </div>
      <RankingTable
        ranking={sorted}
        isLoading={w.rankingLoading}
        sortConfig={sort}
        onSort={handleSort}
        onOpenHistory={w.openHistory}
      />
    </div>
  )
}
