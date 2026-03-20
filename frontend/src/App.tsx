import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'

const AuthPage = lazy(() => import('./pages/AuthPage'))
const HomePage = lazy(() => import('./pages/HomePage'))

function App() {
  return (
    <Suspense fallback={<div className="flex items-center justify-center min-h-screen">Loading...</div>}>
      <Routes>
        <Route path="/" element={<div className="p-8 text-center"><h1 className="text-2xl font-bold">FutSpring</h1></div>} />
        <Route path="/auth" element={<AuthPage />} />
        <Route path="/home" element={<HomePage />} />
      </Routes>
    </Suspense>
  )
}

export default App
