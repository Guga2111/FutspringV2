import { Skeleton } from "@/components/ui/skeleton"

// Shaped like the pelada page: banner card, tab bar, member cards
export function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-5 p-4 md:gap-6 md:p-6">
      <Skeleton className="h-[190px] w-full rounded-tile md:h-[230px]" />
      <Skeleton className="h-10 w-full rounded-full md:w-[340px]" />
      <div className="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-3">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <Skeleton key={i} className="h-[132px] rounded-xl" />
        ))}
      </div>
    </div>
  )
}
