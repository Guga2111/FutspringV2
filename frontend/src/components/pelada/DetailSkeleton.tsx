import { Skeleton } from "../ui/skeleton";

export function DetailSkeleton() {
  return (
    <div>
      <Skeleton className="h-72 w-full rounded-none" />
      <div className="container max-w-4xl mx-auto px-4 py-6">
        <Skeleton className="h-8 w-1/2 mb-3" />
        <Skeleton className="h-4 w-1/3 mb-2" />
        <Skeleton className="h-4 w-1/4 mb-2" />
        <Skeleton className="h-4 w-2/5 mb-6" />
        <Skeleton className="h-6 w-32 mb-4" />
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex items-center gap-3 mb-3">
            <Skeleton className="h-10 w-10 rounded-full flex-shrink-0" />
            <div className="flex-1">
              <Skeleton className="h-4 w-32 mb-1" />
              <Skeleton className="h-3 w-20" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
