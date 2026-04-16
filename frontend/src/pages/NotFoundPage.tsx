import { Link } from 'react-router-dom'
import { Button } from '../components/ui/button'

export default function NotFoundPage() {
  return (
    <div className="page-enter flex flex-col items-center justify-center min-h-screen gap-4 text-center px-4">
      <div className="w-24 h-24 rounded-full bg-muted flex items-center justify-center shadow-md">
        <img src="/gerrard.png" alt="Football" className="w-14 h-14 object-cover rounded-full" />
      </div>
      <h1 className="text-4xl font-bold">404</h1>
      <p className="text-muted-foreground text-lg">Parece que essa página saiu pela linha de fundo!</p>
      <Button asChild variant="default">
        <Link to="/home">Voltar para o Início</Link>
      </Button>
    </div>
  )
}
