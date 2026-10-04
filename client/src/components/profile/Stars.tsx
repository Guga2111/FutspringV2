export function Stars({ count }: { count: number }) {
  return (
    <span className="text-gold text-lg">
      {[1, 2, 3, 4, 5].map((i) => (
        <span key={i}>{i <= count ? '★' : '☆'}</span>
      ))}
    </span>
  )
}
