import { useCallback, useState } from "react"
import { Navigate } from "react-router-dom"
import { getPlatformUsers, PLATFORM_PAGE_SIZE } from "@/api/platform"
import { ListPagination } from "@/components/ListPagination"
import { PageContainer } from "@/components/layout/PageContainer"
import { PlatformLoadError } from "@/components/platform/PlatformLoadError"
import { PlatformPageHeader } from "@/components/platform/PlatformPageHeader"
import { PlatformSearch } from "@/components/platform/PlatformSearch"
import { PlatformUsersList } from "@/components/platform/PlatformUsersList"
import { useDebouncedValue, usePageOf, usePlatformRequest } from "@/components/platform/hooks/usePlatformRequest"
import { Skeleton } from "@/components/ui/skeleton"

export default function AdminUsersPage() {
  const [query, setQuery] = useState("")
  const q = useDebouncedValue(query.trim())
  const [page, setPage] = usePageOf(q)
  const fetcher = useCallback(() => getPlatformUsers(page, q), [page, q])
  const { data, loading, error, notFound, retry } = usePlatformRequest(fetcher, "Não foi possível carregar os usuários")

  if (notFound) return <Navigate to="/404" replace />
  return (
    <PageContainer className="page-enter">
      <PlatformPageHeader area="users" title="Usuários" />
      <PlatformSearch value={query} onChange={setQuery} label="Buscar por nome ou e-mail" />
      {error && <PlatformLoadError message={error} onRetry={retry} />}
      {loading && <Skeleton className="h-[320px] w-full" />}
      {data &&
        (data.items.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nenhum usuário encontrado.</p>
        ) : (
          <>
            <PlatformUsersList users={data.items} />
            <ListPagination page={page} total={data.total} size={PLATFORM_PAGE_SIZE} noun={["usuário", "usuários"]} onPage={setPage} />
          </>
        ))}
    </PageContainer>
  )
}
