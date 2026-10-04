import { Skeleton } from '../ui/skeleton'

export default function DetailSkeleton() {
  return (
    <div className="container max-w-4xl mx-auto px-4 py-6">
      <Skeleton className="h-6 w-1/3 mb-2" />
      <Skeleton className="h-8 w-1/2 mb-3" />
      <Skeleton className="h-5 w-24 mb-6" />
      <Skeleton className="h-6 w-40 mb-4" />
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center gap-3 mb-3">
          <Skeleton className="h-10 w-10 rounded-full flex-shrink-0" />
          <Skeleton className="h-4 w-32" />
        </div>
      ))}
    </div>
  )
}
