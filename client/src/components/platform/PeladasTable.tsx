import { Link } from "react-router-dom"
import type { ReactNode } from "react"
import { Card } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { SubscriptionStatusBadge } from "@/components/platform/SubscriptionStatusBadge"
import type { PlatformPeladaItem } from "@/types/platform"
import { formatBillingDate, formatIsoDate } from "@/utils/platform"

const validity = (item: PlatformPeladaItem) => (item.validUntil ? formatBillingDate(item.validUntil) : "—")
const lastSession = (item: PlatformPeladaItem) => (item.lastSessionDate ? formatIsoDate(item.lastSessionDate) : "Nenhuma")

interface Column {
  header: string
  cell: (item: PlatformPeladaItem) => ReactNode
}

const columns: Column[] = [
  {
    header: "Pelada",
    cell: (item) => (
      <Link to={`/admin/peladas/${item.id}`} className="font-medium underline-offset-4 hover:underline">
        {item.name}
        {item.founder && <span className="ml-1.5 text-caption text-muted-foreground">· Fundadora</span>}
      </Link>
    ),
  },
  { header: "Criador", cell: (item) => <span className="text-muted-foreground">{item.creator.username}</span> },
  { header: "Membros", cell: (item) => <span className="tabular-nums">{item.memberCount}</span> },
  {
    header: "Estado",
    cell: (item) => (
      <span className="flex flex-wrap items-center gap-1.5">
        <SubscriptionStatusBadge status={item.status} />
        {item.pixClaimedAt && <span className="text-caption text-warning">Pix informado</span>}
      </span>
    ),
  },
  { header: "Validade", cell: (item) => <span className="tabular-nums">{validity(item)}</span> },
  { header: "Última sessão", cell: (item) => <span className="tabular-nums text-muted-foreground">{lastSession(item)}</span> },
]

// The page of peladas (sorted and paged by the server): a table from md, link rows below
export function PeladasTable({ items }: { items: PlatformPeladaItem[] }) {
  return (
    <Card className="overflow-hidden p-0">
      <Table className="hidden md:table">
        <TableHeader className="bg-card-elevated">
          <TableRow>
            {columns.map((column) => (
              <TableHead key={column.header}>{column.header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.map((item) => (
            <TableRow key={item.id}>
              {columns.map((column) => (
                <TableCell key={column.header}>{column.cell(item)}</TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <ul className="divide-y divide-border md:hidden">
        {items.map((item) => (
          <li key={item.id}>
            <Link to={`/admin/peladas/${item.id}`} className="flex flex-col gap-1.5 px-4 py-3 hover:bg-row-hover">
              <span className="flex items-center justify-between gap-3">
                <span className="min-w-0 truncate font-medium">{item.name}</span>
                <SubscriptionStatusBadge status={item.status} />
              </span>
              <span className="text-caption text-muted-foreground">
                {item.creator.username} · {item.memberCount} membros · validade {validity(item)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  )
}
