import { Link } from "react-router-dom"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import type { PlatformUserItem } from "@/types/platform"
import { formatBillingDate } from "@/utils/platform"

// Every account, newest first: sign-up, peladas created (to the panel's page), how many it plays in, the trial
export function PlatformUsersList({ users }: { users: PlatformUserItem[] }) {
  return (
    <Card className="p-0">
      <ul className="divide-y divide-border">
        {users.map((user) => (
          <li key={user.id} className="flex flex-col gap-1 px-4 py-3">
            <span className="flex flex-wrap items-center gap-2">
              <span className="font-medium">{user.username}</span>
              {user.isDeleted && (
                <Badge variant="status" className="bg-secondary text-secondary-foreground">
                  Excluída
                </Badge>
              )}
            </span>
            <span className="break-all text-sm text-muted-foreground">{user.email}</span>
            <span className="text-caption text-muted-foreground">
              {user.createdAt ? `Cadastro em ${formatBillingDate(user.createdAt)}` : "Cadastro sem data"} · participa de{" "}
              {user.memberOf} {user.memberOf === 1 ? "pelada" : "peladas"} ·{" "}
              {user.trialUsedAt ? `teste usado em ${formatBillingDate(user.trialUsedAt)}` : "teste não usado"}
            </span>
            {user.peladasCreated.length > 0 && (
              <span className="flex flex-wrap gap-x-3 gap-y-1 text-caption">
                <span className="text-muted-foreground">Criou:</span>
                {user.peladasCreated.map((pelada) => (
                  <Link key={pelada.id} to={`/admin/peladas/${pelada.id}`} className="font-medium underline-offset-4 hover:underline">
                    {pelada.name}
                  </Link>
                ))}
              </span>
            )}
          </li>
        ))}
      </ul>
    </Card>
  )
}
