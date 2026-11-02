import { Skeleton } from "@/components/ui/skeleton"

// Same shape as the loaded page: header, numbers strip, tabs and the first cards
export function ProfileSkeleton() {
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 px-4 py-4 md:px-6 md:py-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:gap-5">
        <Skeleton className="size-20 rounded-full md:size-24" />
        <div className="flex flex-col gap-2 md:pb-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-5 w-56" />
        </div>
      </div>
      <Skeleton className="h-[164px] w-full rounded-lg md:h-[92px]" />
      <Skeleton className="h-10 w-full rounded-full md:w-80" />
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-[120px] rounded-lg lg:col-span-3" />
        <Skeleton className="h-[340px] rounded-lg" />
        <Skeleton className="h-[340px] rounded-lg lg:col-span-2" />
      </div>
    </div>
  )
}
