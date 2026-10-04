import { useState } from 'react'
import {
  type ColumnDef,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import { ArrowUpDown, ArrowUp, ArrowDown, ChevronDown, ChevronRight } from 'lucide-react'
import { Button } from '../ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../ui/table'
import type { UserDailyStatsDTO } from '../../types/daily'

interface PlayerStatsSectionProps {
  stats: UserDailyStatsDTO[]
}

function SortIcon({ sorted }: { sorted: false | 'asc' | 'desc' }) {
  if (sorted === 'asc') return <ArrowUp className="ml-2 h-3 w-3" />
  if (sorted === 'desc') return <ArrowDown className="ml-2 h-3 w-3" />
  return <ArrowUpDown className="ml-2 h-3 w-3" />
}

const columns: ColumnDef<UserDailyStatsDTO>[] = [
  {
    accessorKey: 'username',
    header: ({ column }) => (
      <Button variant="ghost" size="sm" className="-mx-2" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
        Jogador
        <SortIcon sorted={column.getIsSorted()} />
      </Button>
    ),
    cell: ({ row }) => <span>{row.getValue('username')}</span>,
  },
  {
    accessorKey: 'goals',
    header: ({ column }) => (
      <Button variant="ghost" size="sm" className="-mx-2" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
        Gols
        <SortIcon sorted={column.getIsSorted()} />
      </Button>
    ),
    cell: ({ row }) => <span className="tabular-nums">{row.getValue('goals')}</span>,
  },
  {
    accessorKey: 'assists',
    header: ({ column }) => (
      <Button variant="ghost" size="sm" className="-mx-2" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
        Assists
        <SortIcon sorted={column.getIsSorted()} />
      </Button>
    ),
    cell: ({ row }) => <span className="tabular-nums">{row.getValue('assists')}</span>,
  },
  {
    accessorKey: 'matchesPlayed',
    header: ({ column }) => (
      <Button variant="ghost" size="sm" className="-mx-2" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
        Partidas
        <SortIcon sorted={column.getIsSorted()} />
      </Button>
    ),
    cell: ({ row }) => <span className="tabular-nums">{row.getValue('matchesPlayed')}</span>,
  },
  {
    accessorKey: 'wins',
    header: ({ column }) => (
      <Button variant="ghost" size="sm" className="-mx-2" onClick={() => column.toggleSorting(column.getIsSorted() === 'asc')}>
        Vitórias
        <SortIcon sorted={column.getIsSorted()} />
      </Button>
    ),
    cell: ({ row }) => <span className="tabular-nums">{row.getValue('wins')}</span>,
  },
]

export default function PlayerStatsSection({ stats }: PlayerStatsSectionProps) {
  const [expanded, setExpanded] = useState(false)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'goals', desc: true }])

  const table = useReactTable({
    data: stats,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  if (stats.length === 0) return null

  return (
    <section className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <Button variant="ghost" size="sm" className="h-7 gap-1 text-lg font-semibold px-0 hover:bg-transparent" onClick={() => setExpanded((p) => !p)}>
          {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          Estatísticas dos Jogadores
        </Button>
      </div>
      {expanded && (
        <div className="rounded-md border overflow-x-auto">
          <Table>
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <TableHead key={header.id} className="px-2">
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                    </TableHead>
                  ))}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className="px-4 py-3">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
