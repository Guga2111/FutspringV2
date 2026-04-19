import { useState } from "react"
import { Target, HandHelping, Brush, ThumbsDown, Trophy, ChevronDown, ChevronUp } from "lucide-react"
import { Card, CardHeader, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar"
import { Skeleton } from "@/components/ui/skeleton"
import { getFileUrl } from "@/lib/utils"
import type { PeladaAwards, AwardCategory, AwardWinner } from "@/types/pelada"

const ICON_MAP: Record<string, React.ElementType> = {
  ARTILHEIRO: Target,
  GARCOM: HandHelping,
  PUSKAS: Brush,
  BOLA_MURCHA: ThumbsDown,
}

const PLACE_COLORS = [
  "text-yellow-500",
  "text-slate-400",
  "text-amber-700",
]

interface AwardsTabProps {
  awards: PeladaAwards | null
  isLoading: boolean
}

function WinnerRow({ winner, place }: { winner: AwardWinner; place: number }) {
  const initials = winner.username.slice(0, 2).toUpperCase()
  const imgUrl = getFileUrl(winner.userImage)
  return (
    <div className="flex items-center gap-3 py-2">
      <Trophy className={`h-4 w-4 flex-shrink-0 ${PLACE_COLORS[place] ?? "text-muted-foreground"}`} />
      <Avatar className="h-7 w-7 flex-shrink-0">
        {imgUrl && <AvatarImage src={imgUrl} alt={winner.username} />}
        <AvatarFallback className="text-xs font-semibold">{initials}</AvatarFallback>
      </Avatar>
      <span className="flex-1 text-sm font-medium truncate">{winner.username}</span>
      <Badge variant="secondary" className="text-xs font-semibold tabular-nums">
        {winner.count}x
      </Badge>
    </div>
  )
}

function CategoryCard({ category }: { category: AwardCategory }) {
  const [expanded, setExpanded] = useState(false)
  const Icon = ICON_MAP[category.type] ?? Trophy
  const displayed = expanded ? category.topWinners : category.topWinners.slice(0, 3)
  const hasMore = category.topWinners.length > 3

  return (
    <Card className="relative overflow-hidden">
      <CardHeader className="pb-2">
        <div className="flex items-start gap-2">
          <Icon className="h-5 w-5 flex-shrink-0 mt-0.5 text-foreground" />
          <div className="min-w-0">
            <p className="font-semibold leading-tight">{category.name}</p>
            <p className="text-xs text-muted-foreground">{category.description}</p>
          </div>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        {category.topWinners.length === 0 ? (
          <p className="text-sm text-muted-foreground py-2">Sem dados ainda.</p>
        ) : (
          <>
            <div className="divide-y divide-border/50">
              {displayed.map((winner, idx) => (
                <WinnerRow key={winner.userId} winner={winner} place={idx} />
              ))}
            </div>
            {hasMore && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setExpanded(!expanded)}
                className="mt-1 w-full text-xs text-muted-foreground"
              >
                {expanded ? (
                  <><ChevronUp className="h-3 w-3 mr-1" /> Ver menos</>
                ) : (
                  <><ChevronDown className="h-3 w-3 mr-1" /> Ver todos ({category.topWinners.length})</>
                )}
              </Button>
            )}
          </>
        )}
      </CardContent>
      {/* Faded trophy watermark */}
      <Trophy className="absolute bottom-2 right-2 h-16 w-16 text-muted-foreground/10 pointer-events-none" />
    </Card>
  )
}

function SummaryBar({ awards }: { awards: PeladaAwards }) {
  // Collect up to 5 unique players across all categories
  const seen = new Set<number>()
  const uniquePlayers: AwardWinner[] = []
  for (const cat of awards.categories) {
    for (const w of cat.topWinners) {
      if (!seen.has(w.userId)) {
        seen.add(w.userId)
        uniquePlayers.push(w)
        if (uniquePlayers.length >= 5) break
      }
    }
    if (uniquePlayers.length >= 5) break
  }

  return (
    <div className="flex items-center gap-4 mb-4 p-3 rounded-lg bg-muted/40">
      <div className="flex -space-x-2">
        {uniquePlayers.map((p) => {
          const imgUrl = getFileUrl(p.userImage)
          return (
            <Avatar key={p.userId} className="h-8 w-8 border-2 border-background">
              {imgUrl && <AvatarImage src={imgUrl} alt={p.username} />}
              <AvatarFallback className="text-xs font-semibold">
                {p.username.slice(0, 2).toUpperCase()}
              </AvatarFallback>
            </Avatar>
          )
        })}
      </div>
      <p className="text-sm text-muted-foreground">
        <span className="font-semibold text-foreground">{awards.totalCategories}</span> categorias
        {" · "}
        <span className="font-semibold text-foreground">{awards.totalAwardsDistributed}</span> prêmios distribuídos
      </p>
    </div>
  )
}

export function AwardsTab({ awards, isLoading }: AwardsTabProps) {
  if (isLoading) {
    return (
      <div className="mt-2 space-y-3">
        <Skeleton className="h-12 w-full rounded-lg" />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="space-y-2 p-4 border rounded-lg">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (!awards) {
    return (
      <p className="text-sm text-muted-foreground mt-4">Sem dados de prêmios ainda.</p>
    )
  }

  return (
    <div className="mt-2">
      <SummaryBar awards={awards} />
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        {awards.categories.map((cat) => (
          <CategoryCard key={cat.type} category={cat} />
        ))}
      </div>
    </div>
  )
}
