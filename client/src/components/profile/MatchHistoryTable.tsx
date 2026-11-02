import { useMemo, useState } from "react"
import { Link } from "react-router-dom"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import {
  type ColumnDef,
  type PaginationState,
  type SortingState,
  type VisibilityState,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { HugeiconsIcon } from "@hugeicons/react"
import { ArrowDown01Icon, ArrowDown02Icon, ArrowUp02Icon } from "@hugeicons/core-free-icons"
import { ListPagination } from "@/components/ListPagination"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import type { MatchHistoryRow } from "@/types/stats"
import { parseLocalDate } from "@/utils/dates"
import { sessionResultLabel, sessionResultStyle, toSessionResult } from "@/utils/profile"

interface MatchHistoryTableProps {
  matchHistory: MatchHistoryRow[]
  loading: boolean
}

const PAGE_SIZE = 10
const ALL_PELADAS = "all"

function ResultBadge({ result }: { result: string }) {
  const value = toSessionResult(result)
  return (
    <Badge variant="status" className={sessionResultStyle[value]}>
      {sessionResultLabel[value]}
    </Badge>
  )
}

const winPct = (row: MatchHistoryRow) => (row.matchesPlayed > 0 ? Math.round((row.wins / row.matchesPlayed) * 100) : 0)

// numbers are right-aligned under a right-aligned header; the sort arrow shows on the sorted column only
function sortableHeader(label: string, numeric = false): ColumnDef<MatchHistoryRow>["header"] {
  return ({ column }) => {
    const sorted = column.getIsSorted()
    return (
      <Button
        variant="ghost"
        size="sm"
        onClick={() => column.toggleSorting(sorted === "asc")}
        className={cn("-mx-3 h-8 rounded-full px-3 font-normal text-muted-foreground", numeric && "ml-auto flex")}
      >
        {label}
        {sorted && <HugeiconsIcon icon={sorted === "asc" ? ArrowUp02Icon : ArrowDown02Icon} className="size-3.5" />}
      </Button>
    )
  }
}

const numericCell = (value: number | string) => <span className="block text-right tabular-nums">{value}</span>

const columns: ColumnDef<MatchHistoryRow>[] = [
  {
    accessorKey: "date",
    header: sortableHeader("Data"),
    cell: ({ row }) => (
      <Link
        to={`/daily/${row.original.dailyId}`}
        className="whitespace-nowrap font-medium underline-offset-4 hover:underline"
      >
        {format(parseLocalDate(row.original.date), "dd/MM/yyyy")}
      </Link>
    ),
    enableHiding: false,
  },
  {
    accessorKey: "peladaName",
    header: sortableHeader("Pelada"),
    cell: ({ row }) => (
      <Link
        to={`/pelada/${row.original.peladaId}`}
        className="whitespace-nowrap text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
      >
        {row.original.peladaName}
      </Link>
    ),
  },
  { accessorKey: "result", header: sortableHeader("Resultado"), cell: ({ row }) => <ResultBadge result={row.original.result} /> },
  { accessorKey: "goals", header: sortableHeader("Gols", true), cell: ({ row }) => numericCell(row.original.goals) },
  { accessorKey: "assists", header: sortableHeader("Assistências", true), cell: ({ row }) => numericCell(row.original.assists) },
  { accessorKey: "matchesPlayed", header: sortableHeader("Partidas", true), cell: ({ row }) => numericCell(row.original.matchesPlayed) },
  { accessorKey: "wins", header: sortableHeader("Vitórias", true), cell: ({ row }) => numericCell(row.original.wins) },
  { id: "winPct", accessorFn: winPct, header: sortableHeader("% Vitória", true), cell: ({ row }) => numericCell(`${winPct(row.original)}%`) },
]

const COLUMN_LABELS: Record<string, string> = {
  peladaName: "Pelada",
  result: "Resultado",
  goals: "Gols",
  assists: "Assistências",
  matchesPlayed: "Partidas",
  wins: "Vitórias",
  winPct: "% Vitória",
}

// the phone has no column headers, so its sort is a Select over the same sorting state
const MOBILE_SORTS = {
  recent: { label: "Mais recentes", sorting: [{ id: "date", desc: true }] },
  oldest: { label: "Mais antigas", sorting: [{ id: "date", desc: false }] },
  goals: { label: "Mais gols", sorting: [{ id: "goals", desc: true }] },
  assists: { label: "Mais assistências", sorting: [{ id: "assists", desc: true }] },
} satisfies Record<string, { label: string; sorting: SortingState }>
type MobileSort = keyof typeof MOBILE_SORTS

function mobileSortOf(sorting: SortingState): MobileSort | undefined {
  const [first] = sorting
  return (Object.keys(MOBILE_SORTS) as MobileSort[]).find((key) => {
    const [option] = MOBILE_SORTS[key].sorting
    return first && option.id === first.id && option.desc === first.desc
  })
}

// The sessions the user played: a sortable table from md, a list of link rows on a phone; both paged by 10
export function MatchHistoryTable({ matchHistory, loading }: MatchHistoryTableProps) {
  const [sorting, setSorting] = useState<SortingState>([{ id: "date", desc: true }])
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({})
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: PAGE_SIZE })
  const [peladaId, setPeladaId] = useState(ALL_PELADAS)

  const peladas = useMemo(() => {
    const byId = new Map<number, string>()
    for (const row of matchHistory) byId.set(row.peladaId, row.peladaName)
    return [...byId].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
  }, [matchHistory])

  const rows = useMemo(
    () => (peladaId === ALL_PELADAS ? matchHistory : matchHistory.filter((row) => String(row.peladaId) === peladaId)),
    [matchHistory, peladaId],
  )

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnVisibility, pagination },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  })

  const pageRows = table.getRowModel().rows

  function changePelada(value: string) {
    setPeladaId(value)
    setPagination((prev) => ({ ...prev, pageIndex: 0 }))
  }

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-3 space-y-0 p-4 pb-3 md:p-5 md:pb-3">
        <CardTitle className="text-title font-semibold">Histórico de sessões</CardTitle>
        {!loading && matchHistory.length > 0 && (
          <div className="flex w-full flex-wrap items-center gap-2 md:w-auto">
            {peladas.length > 1 && (
              <Select value={peladaId} onValueChange={changePelada}>
                <SelectTrigger className="h-9 min-w-0 flex-1 md:w-[200px] md:flex-none" aria-label="Filtrar por pelada">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value={ALL_PELADAS}>Todas as peladas</SelectItem>
                    {peladas.map((pelada) => (
                      <SelectItem key={pelada.id} value={String(pelada.id)}>
                        {pelada.name}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            )}
            <Select
              value={mobileSortOf(sorting) ?? ""}
              onValueChange={(value) => setSorting(MOBILE_SORTS[value as MobileSort].sorting)}
            >
              <SelectTrigger className="h-9 min-w-0 flex-1 md:hidden" aria-label="Ordenar por">
                <SelectValue placeholder="Ordenar por" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {(Object.keys(MOBILE_SORTS) as MobileSort[]).map((key) => (
                    <SelectItem key={key} value={key}>
                      {MOBILE_SORTS[key].label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" className="hidden md:inline-flex">
                  Colunas
                  <HugeiconsIcon icon={ArrowDown01Icon} />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuGroup>
                  {table
                    .getAllColumns()
                    .filter((column) => column.getCanHide())
                    .map((column) => (
                      <DropdownMenuCheckboxItem
                        key={column.id}
                        checked={column.getIsVisible()}
                        onCheckedChange={(value) => column.toggleVisibility(!!value)}
                      >
                        {COLUMN_LABELS[column.id] ?? column.id}
                      </DropdownMenuCheckboxItem>
                    ))}
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </CardHeader>

      <CardContent className="flex flex-col gap-4 p-0 pb-4 md:pb-5">
        {loading ? (
          <div className="flex flex-col gap-2 px-4 md:px-5">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <p className="px-4 text-body text-muted-foreground md:px-5">Sem histórico de sessões.</p>
        ) : (
          <>
            {/* phone: one link row per session */}
            <ul className="flex flex-col border-t md:hidden">
              {pageRows.map((row) => {
                const item = row.original
                const date = parseLocalDate(item.date)
                return (
                  <li key={row.id} className="border-b last:border-b-0">
                    <Link
                      to={`/daily/${item.dailyId}`}
                      className="flex items-center gap-3 px-4 py-3 transition-colors hover:bg-row-hover focus-visible:bg-row-hover focus-visible:outline-none"
                    >
                      <div className="flex w-11 shrink-0 flex-col items-center rounded-tile bg-card-elevated py-1">
                        <span className="font-display text-title font-bold leading-none tabular-nums">{format(date, "dd")}</span>
                        <span className="text-overline uppercase text-muted-foreground">{format(date, "MMM", { locale: ptBR })}</span>
                      </div>
                      <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
                        <span className="w-full truncate text-body font-medium">{item.peladaName}</span>
                        <ResultBadge result={item.result} />
                      </div>
                      <dl className="flex shrink-0 gap-3 text-right">
                        {[
                          ["G", item.goals, "Gols"],
                          ["A", item.assists, "Assistências"],
                          ["P", item.matchesPlayed, "Partidas"],
                        ].map(([short, value, label]) => (
                          <div key={short} className="flex w-6 flex-col items-end">
                            <dt className="text-caption text-muted-foreground">
                              <abbr title={String(label)} className="no-underline">{short}</abbr>
                            </dt>
                            <dd className="text-body font-semibold tabular-nums">{value}</dd>
                          </div>
                        ))}
                      </dl>
                    </Link>
                  </li>
                )
              })}
            </ul>

            {/* md+: the sortable table */}
            <div className="hidden border-t md:block">
              <Table>
                <TableHeader>
                  {table.getHeaderGroups().map((headerGroup) => (
                    <TableRow key={headerGroup.id} className="hover:bg-transparent">
                      {headerGroup.headers.map((header) => (
                        <TableHead key={header.id} className="h-10 px-5">
                          {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                        </TableHead>
                      ))}
                    </TableRow>
                  ))}
                </TableHeader>
                <TableBody>
                  {pageRows.map((row) => (
                    <TableRow key={row.id} className="hover:bg-row-hover">
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id} className="px-5 py-3">
                          {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            {rows.length > PAGE_SIZE && (
              <div className="px-4 md:px-5">
                <ListPagination
                  page={pagination.pageIndex}
                  total={rows.length}
                  size={PAGE_SIZE}
                  noun={["sessão", "sessões"]}
                  onPage={(page) => setPagination((prev) => ({ ...prev, pageIndex: page }))}
                />
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}
