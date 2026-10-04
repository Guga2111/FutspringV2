import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useMyPeladasContext } from '@/hooks/useMyPeladasContext'
import { PeladaCard } from '@/components/pelada/PeladaCard'

function PeladaCardSkeleton() {
  return (
    <Card className="overflow-hidden rounded-xl border shadow-sm">
      <Skeleton className="h-40 w-full rounded-none" />
      <CardContent className="p-4">
        <Skeleton className="h-5 w-3/4 mb-2" />
        <Skeleton className="h-4 w-1/2 mb-2" />
        <Skeleton className="h-4 w-1/3" />
      </CardContent>
    </Card>
  )
}


export default function HomePage() {
  const { peladas, loading, error, reload, openCreatePelada } = useMyPeladasContext()

  return (
    <div className="page-enter flex flex-1 flex-col">
      <main className="flex-1 container max-w-5xl mx-auto px-4 py-8">
        {/* Hero Card */}
        <div
          className="relative rounded-2xl overflow-hidden mb-8"
          style={{ minHeight: 200 }}
        >
          <img
            src="/ronaldo.jpg"
            alt="Hero background"
            className="absolute inset-0 w-full h-full object-cover object-top blur-[3px]"
          />
          <div className="absolute inset-0 bg-black/55" />
          <div className="relative z-10 flex items-center justify-between px-8 py-8 h-full">
            <div className="flex flex-col gap-3 max-w-md">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-white bg-[#1a7a4a]/80 border border-white/20 rounded-full px-3 py-1 w-fit backdrop-blur-sm">
                ✦ Pronto para jogar
              </span>
              <h2 className="text-3xl font-extrabold text-white leading-tight">
                Organize sua próxima pelada
              </h2>
              <p className="text-sm text-white/75">
                Chame seus amigos, escolha um dia, e nunca mais perca algum jogo novamente.<br />
                FutSpring faz ser facil organizar seus jogos de futebol semanais.
              </p>
              <div className="flex items-center gap-5 mt-2">
                <div>
                  <p className="text-2xl font-extrabold text-white">{peladas.length}</p>
                  <p className="text-xs text-white/60">Peladas Ativas</p>
                </div>
                <div className="w-px h-10 bg-white/25" />
                <div>
                  <p className="text-2xl font-extrabold text-white">
                    {peladas.reduce((sum, p) => sum + p.memberCount, 0)}
                  </p>
                  <p className="text-xs text-white/60">Total de Jogadores</p>
                </div>
              </div>
            </div>
            <Button variant="gradient" className="shrink-0" onClick={openCreatePelada}>
              + Nova Pelada
            </Button>
          </div>
        </div>

        <h1 className="text-xl font-bold tracking-tight mb-6">Minhas Peladas</h1>
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
            <PeladaCardSkeleton />
          </div>
        ) : error ? (
          <Alert variant="destructive" className="flex items-center justify-between gap-4">
            <AlertDescription>Não foi possível carregar suas peladas.</AlertDescription>
            <Button variant="outline" size="sm" onClick={reload}>Tentar novamente</Button>
          </Alert>
        ) : peladas.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="w-20 h-20 rounded-full bg-muted flex items-center justify-center shadow-md mb-4">
              <img src="/gerrard.png" alt="Football" className="w-11 h-11 object-cover rounded-full" />
            </div>
            <p className="text-muted-foreground text-lg mb-6">
              Você ainda não está em nenhuma pelada. Crie uma para começar.
            </p>
            <Button variant="gradient" onClick={openCreatePelada}>+ Nova Pelada</Button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {peladas.map((pelada) => (
              <PeladaCard key={pelada.id} pelada={pelada} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
