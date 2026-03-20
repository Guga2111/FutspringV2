import NavBar from '../components/NavBar'

export default function HomePage() {
  return (
    <div className="min-h-screen flex flex-col">
      <NavBar />
      <main className="flex flex-1 items-center justify-center">
        <h1 className="text-2xl font-bold">My Peladas - coming soon</h1>
      </main>
    </div>
  )
}
