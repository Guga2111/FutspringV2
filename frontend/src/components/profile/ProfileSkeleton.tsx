import { SkeletonBlock } from './SkeletonBlock'

export function ProfileSkeleton() {
  return (
    <div>
      <SkeletonBlock className="w-full h-[200px]" />
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        <div className="flex items-end gap-4 -mt-12 mb-6">
          <SkeletonBlock className="h-24 w-24 rounded-full border-4 border-background" />
          <div className="flex-1 pb-2 space-y-2">
            <SkeletonBlock className="h-6 w-40" />
            <SkeletonBlock className="h-5 w-24" />
          </div>
        </div>
        <section className="mb-10">
          <SkeletonBlock className="h-6 w-32 mb-2" />
          <SkeletonBlock className="h-px w-full mb-6" />
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-7 gap-4">
            {[0, 1, 2, 3, 4, 5, 6].map((i) => (
              <SkeletonBlock key={i} className="h-24 w-full" />
            ))}
          </div>
        </section>
        <section className="mb-10">
          <SkeletonBlock className="h-6 w-32 mb-2" />
          <SkeletonBlock className="h-px w-full mb-6" />
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {[0, 1, 2, 3].map((i) => (
              <SkeletonBlock key={i} className="h-24 w-full" />
            ))}
          </div>
        </section>
        <section className="mb-10">
          <SkeletonBlock className="h-6 w-32 mb-2" />
          <SkeletonBlock className="h-px w-full mb-6" />
          <SkeletonBlock className="h-[200px] w-full" />
        </section>
        <section className="mb-10">
          <SkeletonBlock className="h-6 w-32 mb-2" />
          <SkeletonBlock className="h-px w-full mb-6" />
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {[0, 1, 2].map((i) => (
              <SkeletonBlock key={i} className="h-36 w-full rounded-xl" />
            ))}
          </div>
        </section>
        <section className="mb-10">
          <SkeletonBlock className="h-6 w-32 mb-2" />
          <SkeletonBlock className="h-px w-full mb-6" />
          <SkeletonBlock className="h-40 w-full" />
        </section>
      </div>
    </div>
  )
}
