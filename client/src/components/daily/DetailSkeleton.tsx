import { PageContainer } from "@/components/layout/PageContainer"
import { Skeleton } from '@/components/ui/skeleton'

// Shaped like the session page: header, attendance card, list
export default function DetailSkeleton() {
  return (
    <PageContainer className="gap-7">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-8 w-72 max-w-full" />
        <Skeleton className="h-4 w-48" />
      </div>
      <Skeleton className="h-[150px] w-full rounded-tile" />
      <Skeleton className="h-6 w-56" />
      <div className="grid grid-cols-1 gap-4 min-[1100px]:grid-cols-2">
        <Skeleton className="h-64 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    </PageContainer>
  )
}
