interface StarRatingProps {
  stars: number
}

export default function StarRating({ stars }: StarRatingProps) {
  return (
    <span className="text-yellow-400 text-xs">
      {Array.from({ length: 5 }, (_, i) => (
        <span key={i}>{i < stars ? '★' : '☆'}</span>
      ))}
    </span>
  )
}
