import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import PrivateRoute from './components/PrivateRoute'
import ErrorBoundary from './components/ErrorBoundary'
import ScrollToTop from './components/ScrollToTop'

const AuthPage = lazy(() => import('./pages/AuthPage'))
const HomePage = lazy(() => import('./pages/HomePage'))
const PeladaDetailPage = lazy(() => import('./pages/PeladaDetailPage'))
const DailyDetailPage = lazy(() => import('./pages/DailyDetailPage'))
const ProfilePage = lazy(() => import('./pages/ProfilePage'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'))

const fallback = <div className="flex items-center justify-center min-h-screen">Loading...</div>

function App() {
  return (
    <>
      <ScrollToTop />
      <Suspense fallback={fallback}>
        <Routes>
          <Route path="/" element={<div className="flex items-center justify-center min-h-screen"><h1 className="text-2xl font-bold">FutSpring — Welcome</h1></div>} />
          <Route path="/auth" element={<ErrorBoundary><AuthPage /></ErrorBoundary>} />
          <Route path="/home" element={<ErrorBoundary><PrivateRoute><HomePage /></PrivateRoute></ErrorBoundary>} />
          <Route path="/pelada/:id" element={<ErrorBoundary><PrivateRoute><PeladaDetailPage /></PrivateRoute></ErrorBoundary>} />
          <Route path="/daily/:id" element={<ErrorBoundary><PrivateRoute><DailyDetailPage /></PrivateRoute></ErrorBoundary>} />
          <Route path="/profile/:id" element={<ErrorBoundary><PrivateRoute><ProfilePage /></PrivateRoute></ErrorBoundary>} />
          <Route path="*" element={<ErrorBoundary><NotFoundPage /></ErrorBoundary>} />
        </Routes>
      </Suspense>
    </>
  )
}

export default App
