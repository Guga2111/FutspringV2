import { Link } from 'react-router-dom'
import { Button } from '../components/ui/button'

export default function NotFoundPage() {
  return (
    <div className="page-enter flex flex-col items-center justify-center min-h-screen gap-4 text-center px-4">
      <span className="text-6xl">⚽</span>
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground text-lg">Looks like this page went out of bounds!</p>
      <Button asChild variant="default">
        <Link to="/home">Back to Home</Link>
      </Button>
    </div>
  )
}
