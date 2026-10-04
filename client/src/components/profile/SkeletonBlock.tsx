export function SkeletonBlock({ className }: { className: string }) {
  return <div className={`bg-muted rounded animate-pulse ${className}`} />
}
