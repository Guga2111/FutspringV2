import { lazy, Suspense } from "react"
import { Routes, Route } from "react-router-dom"
import PrivateRoute from "@/components/PrivateRoute"
import ErrorBoundary from "@/components/ErrorBoundary"
import ScrollToTop from "@/components/ScrollToTop"
import { AppLayout } from "@/components/layout/AppLayout"

const LandingPage = lazy(() => import("@/pages/LandingPage"))
const AuthPage = lazy(() => import("@/pages/AuthPage"))
const ForgotPasswordPage = lazy(() => import("@/pages/ForgotPasswordPage"))
const ResetPasswordPage = lazy(() => import("@/pages/ResetPasswordPage"))
const HomePage = lazy(() => import("@/pages/HomePage"))
const PeladaDetailPage = lazy(() => import("@/pages/PeladaDetailPage"))
const DailyDetailPage = lazy(() => import("@/pages/DailyDetailPage"))
const ProfilePage = lazy(() => import("@/pages/ProfilePage"))
const NotFoundPage = lazy(() => import("@/pages/NotFoundPage"))

const fallback = <div className="flex items-center justify-center min-h-screen">Carregando…</div>

function App() {
  return (
    <>
      <ScrollToTop />
      <Suspense fallback={fallback}>
        <Routes>
          <Route path="/" element={<ErrorBoundary><LandingPage /></ErrorBoundary>} />
          <Route path="/auth" element={<ErrorBoundary><AuthPage /></ErrorBoundary>} />
          <Route path="/forgot-password" element={<ErrorBoundary><ForgotPasswordPage /></ErrorBoundary>} />
          <Route path="/reset-password" element={<ErrorBoundary><ResetPasswordPage /></ErrorBoundary>} />
          {/* Private pages share the sidebar shell */}
          <Route element={<ErrorBoundary><PrivateRoute><AppLayout /></PrivateRoute></ErrorBoundary>}>
            <Route path="/home" element={<ErrorBoundary><HomePage /></ErrorBoundary>} />
            <Route path="/pelada/:id" element={<ErrorBoundary><PeladaDetailPage /></ErrorBoundary>} />
            <Route path="/daily/:id" element={<ErrorBoundary><DailyDetailPage /></ErrorBoundary>} />
            <Route path="/profile/:id" element={<ErrorBoundary><ProfilePage /></ErrorBoundary>} />
          </Route>
          <Route path="*" element={<ErrorBoundary><NotFoundPage /></ErrorBoundary>} />
        </Routes>
      </Suspense>
    </>
  )
}

export default App
